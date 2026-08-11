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

    private val dispatcher: CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(1, "QwenEmbedder")

    companion object {
        private const val HIDDEN = 1024
        private const val MAX_TOKENS = 128
        private const val VOCAB_SIZE = 151669

        internal fun requireTokenCount(tokenCount: Int) {
            require(tokenCount > 0) { "Embedding input must contain at least one token" }
        }
    }

    init {
        embeddingTable = mmapRawHalf(File(modelDir, "embeddings_fp16.bin"))

        model = CompiledModel.create(
            File(modelDir, "qwen3emb_gpu_fp16.tflite").absolutePath,
            CompiledModel.Options(Accelerator.GPU),
            null,
        )

        inputBuffer = model.createInputBuffers()[0]
        outputBuffer = model.createOutputBuffers()[0]

        runCatching {
            inputBuffer.writeFloat(FloatArray(MAX_TOKENS * HIDDEN))
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
        val numTokens = tokenIds.size.coerceAtMost(MAX_TOKENS)
        requireTokenCount(numTokens)

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

        model.run(listOf(inputBuffer), listOf(outputBuffer))
        val hidden = outputBuffer.readFloat() // [MAX_TOKENS * HIDDEN]

        // last-token pooling: take the last real token's hidden state
        val last = (numTokens - 1) * HIDDEN
        val emb = FloatArray(HIDDEN)
        var sumSq = 0f
        for (i in 0 until HIDDEN) {
            emb[i] = hidden[last + i]
            sumSq += emb[i] * emb[i]
        }
        // L2 normalize
        val norm = sqrt(sumSq).coerceAtLeast(1e-12f)
        for (i in 0 until HIDDEN) {
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
        val channel = RandomAccessFile(file, "r").channel
        val map: MappedByteBuffer = channel.map(
            FileChannel.MapMode.READ_ONLY, 0, file.length())
        return map.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
    }
}
