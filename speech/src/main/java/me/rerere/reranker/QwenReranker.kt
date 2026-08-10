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
 * - `qwen3rerank_gpu_fp16.tflite`  — 28-layer decoder + 2-logit head
 * - `embeddings_fp16.bin`          — fp16 table [151669, 1024]
 * - `vocab.json` + `merges.txt`    — Qwen byte-level BPE tokenizer
 *
 * CompiledModel scaffolding rules: confined dispatcher, warm-up, buffer reuse,
 * strict GPU accelerator, readback-is-sync.
 */
class QwenReranker(private val modelDir: File) : Closeable {

    private val tokenizer = QwenBpeTokenizer(
        File(modelDir, "vocab.json"), File(modelDir, "merges.txt"))

    private val embeddingTable: ShortBuffer
    private val model: CompiledModel

    private val inputBuffer: TensorBuffer
    private val outputBuffer: TensorBuffer

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
        private const val HIDDEN = 1024
        private const val MAX_TOKENS = 256
        private const val VOCAB_SIZE = 151669

        val DEFAULT_INSTRUCTION =
            "Given a web search query, retrieve relevant passages that answer the query"

        private const val SYSTEM_PROMPT =
            "Judge whether the Document meets the requirements based on the Query and " +
                "the Instruct provided. Note that the answer can only be \"yes\" or \"no\"."
    }

    init {
        embeddingTable = mmapRawHalf(File(modelDir, "embeddings_fp16.bin"))

        model = CompiledModel.create(
            File(modelDir, "qwen3rerank_gpu_fp16.tflite").absolutePath,
            CompiledModel.Options(Accelerator.GPU),
            null,
        )

        inputBuffer = model.createInputBuffers()[0]
        outputBuffer = model.createOutputBuffers()[0]

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
            inputBuffer.writeFloat(FloatArray(MAX_TOKENS * HIDDEN))
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
        val numTokens = tokenIds.size.coerceAtMost(MAX_TOKENS)

        // Host-side embedding lookup into the input buffer (right-padded).
        val flat = FloatArray(MAX_TOKENS * HIDDEN)
        for (t in 0 until numTokens) {
            val row = tokenIds[t]
            if (row < 0 || row >= VOCAB_SIZE) continue
            embeddingTable.position(row * HIDDEN)
            val off = t * HIDDEN
            for (d in 0 until HIDDEN) {
                flat[off + d] = Npy.halfToFloat(embeddingTable.get())
            }
        }
        inputBuffer.writeFloat(flat)

        // GPU inference — readback is the sync point.
        model.run(listOf(inputBuffer), listOf(outputBuffer))
        val logits = outputBuffer.readFloat() // [MAX_TOKENS * 2]

        // Pool: softmax(yes) at last real token.
        val last = numTokens - 1
        val no = logits[last * 2]
        val yes = logits[last * 2 + 1]
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
        val userContent = tokenizer.encode(
            "<Instruct>: $instruction\n<Query>: $query\n<Document>: $doc")
        val size = 12 + systemContent.size + userContent.size // 12 control tokens
        val ids = IntArray(size)
        var p = 0
        // <|im_start|>system\n
        ids[p++] = imStart; ids[p++] = systemToken; ids[p++] = newline
        systemContent.copyInto(ids, p); p += systemContent.size
        ids[p++] = imEnd; ids[p++] = newline
        // <|im_start|>user\n
        ids[p++] = imStart; ids[p++] = userToken; ids[p++] = newline
        userContent.copyInto(ids, p); p += userContent.size
        ids[p++] = imEnd; ids[p++] = newline
        // <|im_start|>assistant\n
        ids[p++] = imStart; ids[p] = assistantToken
        return ids
    }

    override fun close() {
        runCatching { inputBuffer.close() }
        runCatching { outputBuffer.close() }
        runCatching { model.close() }
    }

    private fun mmapRawHalf(file: File): ShortBuffer {
        val channel = RandomAccessFile(file, "r").channel
        val map: MappedByteBuffer = channel.map(
            FileChannel.MapMode.READ_ONLY, 0, file.length())
        return map.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
    }
}