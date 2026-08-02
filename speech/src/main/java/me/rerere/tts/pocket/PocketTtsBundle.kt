package me.rerere.tts.pocket

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File

/** Opens the complete five-graph Pocket TTS ONNX bundle without running inference. */
class PocketTtsBundle private constructor(
    private val sessions: MutableMap<Graph, OrtSession>,
    val environment: OrtEnvironment,
) : AutoCloseable {
    enum class Graph(val fileName: String) {
        TEXT_CONDITIONER("text_conditioner.onnx"),
        ENCODER("encoder.onnx"),
        LM_MAIN("lm_main.int8.onnx"),
        LM_FLOW("lm_flow.int8.onnx"),
        DECODER("decoder.int8.onnx"),
    }

    data class GraphInfo(
        val graph: Graph,
        val inputs: Set<String>,
        val outputs: Set<String>,
    )

    fun session(graph: Graph): OrtSession = sessions.getValue(graph)

    /** Closes and forgets one graph session, e.g. the encoder after caching the voice embedding. */
    fun release(graph: Graph) {
        sessions.remove(graph)?.close()
    }

    fun graphInfo(): List<GraphInfo> = sessions.map { (graph, session) ->
        GraphInfo(graph, session.inputInfo.keys, session.outputInfo.keys)
    }

    override fun close() {
        sessions.values.forEach(OrtSession::close)
    }

    companion object {
        val requiredFiles: List<String> = Graph.entries.map(Graph::fileName) + listOf(
            "vocab.json",
            "token_scores.json",
            "tokenizer.model",
            "manifest.json",
        )

        fun open(
            directory: File,
            environment: OrtEnvironment? = null,
        ): PocketTtsBundle {
            require(directory.isDirectory) { "Pocket TTS bundle directory does not exist: $directory" }

            val missing = requiredFiles.filterNot { File(directory, it).isFile }
            require(missing.isEmpty()) { "Pocket TTS bundle is missing: ${missing.joinToString()}" }

            val opened = linkedMapOf<Graph, OrtSession>()
            try {
                val ortEnvironment = environment ?: OrtEnvironment.getEnvironment()
                Graph.entries.forEach { graph ->
                    opened[graph] = ortEnvironment.createSession(File(directory, graph.fileName).path)
                }
                return PocketTtsBundle(opened, ortEnvironment)
            } catch (error: Throwable) {
                opened.values.forEach(OrtSession::close)
                throw error
            }
        }
    }
}
