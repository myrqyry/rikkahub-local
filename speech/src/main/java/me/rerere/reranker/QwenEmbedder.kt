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
import kotlin.math.sqrt

class QwenEmbedder(private val modelDir: File) : Closeable {

    private val tokenizer = QwenBpeTokenizer(
        File(modelDir, "vocab.json"), File(modelDir, "merges.txt"))

    private val embeddingTable: ShortBuffer
    private val model: CompiledModel

    private val inputBuffer: TensorBuffer
    private val outputBuffer: TensorBuffer

    // Tensor geometry derived from the model at init, not hardcoded: a shape mismatch
    // otherwise overruns native memory (the Whisper tiny crash class).
    private val hiddenSize: Int
    private val maxTokens: Int
    private val vocabSize: Long

    private val dispatcher: CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(1, "QwenEmbedder")

    companion object {
        internal fun requireTokenCount(tokenCount: Int) {
            require(tokenCount > 0) { "Embedding input must contain at least one token" }
        }
    }

    init {
        model = CompiledModel.create(
            File(modelDir, "qwen3emb_gpu_fp16.tflite").absolutePath,
            CompiledModel.Options(Accelerator.GPU),
            null,
        )

        inputBuffer = model.createInputBuffers()[0]
        outputBuffer = model.createOutputBuffers()[0]

        val inputShape = readModelShapes(File(modelDir, "qwen3emb_gpu_fp16.tflite"))?.first
            ?: error("Could not read embedder input shape")
        require(inputShape.isNotEmpty()) { "Qwen embedder input has no shape" }
        hiddenSize = inputShape.last()
        maxTokens = (inputShape.fold(1) { acc, dim -> acc * dim } / hiddenSize).coerceAtLeast(1)
        embeddingTable = mmapRawHalf(File(modelDir, "embeddings_fp16.bin"))
        vocabSize = (embeddingTable.capacity() / hiddenSize).toLong()

        runCatching {
            inputBuffer.writeFloat(FloatArray(maxTokens * hiddenSize))
            model.run(listOf(inputBuffer), listOf(outputBuffer))
        }
    }

    suspend fun embed(text: String): FloatArray = withContext(dispatcher) {
        val tokenIds = tokenizer.encode(text)
        embedTokens(tokenIds)
    }

    suspend fun embed(tokens: IntArray): FloatArray = withContext(dispatcher) {
        embedTokens(tokens)
    }

    suspend fun embedBatch(texts: List<String>): List<FloatArray> = withContext(dispatcher) {
        texts.map { embedTokens(tokenizer.encode(it)) }
    }

    private fun embedTokens(tokenIds: IntArray): FloatArray {
        val numTokens = tokenIds.size.coerceAtMost(maxTokens)
        requireTokenCount(numTokens)

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

        model.run(listOf(inputBuffer), listOf(outputBuffer))
        val hidden = outputBuffer.readFloat() // [maxTokens * hiddenSize]

        // last-token pooling: take the last real token's hidden state
        val last = (numTokens - 1) * hiddenSize
        val emb = FloatArray(hiddenSize)
        var sumSq = 0f
        for (i in 0 until hiddenSize) {
            emb[i] = hidden[last + i]
            sumSq += emb[i] * emb[i]
        }
        // L2 normalize
        val norm = sqrt(sumSq).coerceAtLeast(1e-12f)
        for (i in 0 until hiddenSize) {
            emb[i] /= norm
        }
        return emb
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
