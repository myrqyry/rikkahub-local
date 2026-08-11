package me.rerere.reranker

import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.tts.qwen3.Npy
import me.rerere.tts.qwen3.QwenBpeTokenizer
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.ShortBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

/**
 * On-device RAG reranker using Qwen3-Reranker-0.6B via LiteRT CompiledModel GPU.
 *
 * From [litert-community/Qwen3-Reranker-0.6B-LiteRT](https://huggingface.co/litert-community/Qwen3-Reranker-0.6B-LiteRT).
 * Host embeds query+doc tokens against `embeddings_fp16.bin`, feeds the tensor
 * to the GPU `.tflite`, scores `P(yes)` from the baked 2-logit (no,yes) head.
 *
 * Files expected in [modelDir]:
 * - `qwen3rerank_gpu_fp16.tflite`  — decoder + 2-logit head
 * - `embeddings_fp16.bin`          — fp16 table
 * - `vocab.json` + `merges.txt`    — Qwen byte-level BPE tokenizer
 *
 * CompiledModel scaffolding rules: confined dispatcher, warm-up, buffer reuse,
 * strict GPU accelerator, readback-is-sync. Tensor geometry is derived from the
 * model at init rather than hardcoded.
 */
class QwenReranker(private val modelDir: File) : Closeable {

    private val tokenizer = QwenBpeTokenizer(
        File(modelDir, "vocab.json"), File(modelDir, "merges.txt"))

    private val embeddingTable: ShortBuffer
    private val model: CompiledModel

    private val inputBuffer: TensorBuffer
    private val outputBuffer: TensorBuffer

    // Tensor geometry derived from the model at init, not hardcoded.
    private val hiddenSize: Int
    private val maxTokens: Int
    private val vocabSize: Long
    private val logitWidth: Int

    private val dispatcher: CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(1, "QwenReranker")

    // Special token ids resolved from the vocab at init.
    private val imStart: Int
    private val imEnd: Int
    private val newline: Int
    private val assistantToken: Int
    private val systemToken: Int
    private val userToken: Int

    companion object {
        private const val TAG = "QwenReranker"

        val DEFAULT_INSTRUCTION =
            "Given a web search query, retrieve relevant passages that answer the query"

        private const val SYSTEM_PROMPT =
            "Judge whether the Document meets the requirements based on the Query and " +
                "the Instruct provided. Note that the answer can only be \"yes\" or \"no\"."
    }

    init {
        model = CompiledModel.create(
            File(modelDir, "qwen3rerank_gpu_fp16.tflite").absolutePath,
            CompiledModel.Options(Accelerator.GPU),
            null,
        )

        inputBuffer = model.createInputBuffers()[0]
        outputBuffer = model.createOutputBuffers()[0]

        val shapes = readModelShapes(File(modelDir, "qwen3rerank_gpu_fp16.tflite"))
            ?: error("Could not read reranker model shapes")
        val inputShape = shapes.first
        require(inputShape.isNotEmpty()) { "Qwen reranker input has no shape" }
        hiddenSize = inputShape.last()
        maxTokens = (inputShape.fold(1) { acc, dim -> acc * dim } / hiddenSize).coerceAtLeast(1)
        val outputShape = shapes.second
        logitWidth = outputShape.getOrElse(1) { 2 }

        embeddingTable = mmapRawHalf(File(modelDir, "embeddings_fp16.bin"))
        vocabSize = (embeddingTable.capacity() / hiddenSize).toLong()

        // Resolve special tokens by encoding them.
        imStart = tokenizer.encode("<|im_start|>")[0]
        imEnd = tokenizer.encode("<|im_end|>")[0]
        newline = tokenizer.encode("\n")[0]
        // ponytail: known single-token IDs from Qwen3-TTS companion constants
        assistantToken = 77091
        systemToken = 8948
        userToken = 882

        // ponytail: warm-up so first real query doesn't pay GPU compile
        runCatching {
            inputBuffer.writeFloat(FloatArray(maxTokens * hiddenSize))
            model.run(listOf(inputBuffer), listOf(outputBuffer))
        }
    }

    /**
     * Score each document against [query]. Higher = more relevant.
     * Runs on confined dispatcher; safe from any coroutine.
     */
    suspend fun score(
        query: String,
        documents: List<String>,
        instruction: String = DEFAULT_INSTRUCTION,
    ): List<Float> = withContext(dispatcher) {
        documents.map { doc -> scoreOne(query, doc, instruction) }
    }

    /**
     * Score and sort documents by relevance, descending.
     */
    suspend fun rerank(
        query: String,
        documents: List<String>,
        instruction: String = DEFAULT_INSTRUCTION,
    ): List<Pair<String, Float>> = withContext(dispatcher) {
        documents
            .map { it to scoreOne(query, it, instruction) }
            .sortedByDescending { it.second }
    }

    // -- internals ----------------------------------------------------------------

    private fun scoreOne(query: String, doc: String, instruction: String): Float {
        val tokenIds = buildPrompt(query, doc, instruction)
        val numTokens = tokenIds.size

        // Host-side embedding lookup into the input buffer (right-padded).
        val flat = FloatArray(maxTokens * hiddenSize)
        for (t in 0 until numTokens) {
            val row = tokenIds[t]
            if (row < 0 || row >= vocabSize) continue
            embeddingTable.position((row * hiddenSize).toInt())
            val off = t * hiddenSize
            for (d in 0 until hiddenSize) {
                flat[off + d] = Npy.halfToFloat(embeddingTable.get())
            }
        }
        inputBuffer.writeFloat(flat)

        // GPU inference — readback is the sync point.
        model.run(listOf(inputBuffer), listOf(outputBuffer))
        val logits = outputBuffer.readFloat() // [maxTokens * logitWidth]

        // Pool: softmax(yes) at last real token.
        val last = numTokens - 1
        val no = logits[last * logitWidth]
        val yes = logits[last * logitWidth + 1]
        val maxVal = maxOf(no, yes)
        val eNo = exp(no - maxVal)
        val eYes = exp(yes - maxVal)
        return eYes / (eNo + eYes)
    }

    /**
     * Build the Qwen3 chat-template prompt token sequence.
     *
     * Pattern:
     * `<|im_start|>system\n{SYSTEM_PROMPT}<|im_end|>\n<|im_start|>user\n<Instruct>: ...<Query>: ...<Document>: ...<|im_end|>\n<|im_start|>assistant\n`
     */
    private fun buildPrompt(query: String, doc: String, instruction: String): IntArray {
        val systemContent = tokenizer.encode(SYSTEM_PROMPT)
        val userPrefix = tokenizer.encode(
            "<Instruct>: $instruction\n<Query>: $query\n<Document>: ")
        val document = tokenizer.encode(doc)
        val prefix = IntArray(8 + systemContent.size + userPrefix.size)
        var p = 0
        // <|im_start|>system\n
        prefix[p++] = imStart; prefix[p++] = systemToken; prefix[p++] = newline
        systemContent.copyInto(prefix, p); p += systemContent.size
        prefix[p++] = imEnd; prefix[p++] = newline
        // <|im_start|>user\n
        prefix[p++] = imStart; prefix[p++] = userToken; prefix[p++] = newline
        userPrefix.copyInto(prefix, p)
        val suffix = intArrayOf(imEnd, newline, imStart, assistantToken)
        return buildTruncatedPrompt(prefix, document, suffix, maxTokens)
    }

    override fun close() {
        runCatching { inputBuffer.close() }
        runCatching { outputBuffer.close() }
        runCatching { model.close() }
    }

    private fun mmapRawHalf(file: File): ShortBuffer {
        RandomAccessFile(file, "r").use { raf ->
            raf.channel.use { channel ->
                return channel.map(
                    FileChannel.MapMode.READ_ONLY, 0, file.length()
                ).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            }
        }
    }

    /** Read the model's default input/output tensor shapes via the TFLite runtime. */
    private fun readModelShapes(modelFile: File): Pair<IntArray, IntArray>? =
        runCatching {
            val interp = org.tensorflow.lite.Interpreter(modelFile)
            try {
                interp.getInputTensor(0).shape() to interp.getOutputTensor(0).shape()
            } finally {
                interp.close()
            }
        }.getOrNull()
}
