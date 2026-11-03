package me.rerere.tts.qwen3

import java.io.File
import me.rerere.locallm.ModelInstall
import me.rerere.locallm.VerifiedModelArtifact

/**
 * The original host-orchestrated Qwen3-TTS LiteRT graphs are now located
 * under `legacy/` in the publisher repository. The new top-level .litertlm
 * bundles have a different runtime contract and cannot be fed into our
 * three-graph CompiledModel host loop.
 *
 * All source assets are pinned to an immutable Hugging Face revision and to
 * their published SHA-256/byte lengths. Local tables are still flattened
 * because the existing engine opens them by basename.
 */
data class Qwen3TtsCatalogFile(
    val remote: String,
    val local: String,
    val bytes: Long,
    val sha256: String,
) {
    val expected: VerifiedModelArtifact get() = VerifiedModelArtifact(bytes, sha256)
}

data class Qwen3TtsCatalogEntry(
    val displayName: String,
    val modelId: String,
    val description: String,
    val sizeBytes: Long,
    val license: String,
    val sampleRate: Int = 24000,
) {
    companion object {
        const val REVISION = "d6ba751e42044ce443bab046a80a581076d9fc90"
    }

    val files: List<Qwen3TtsCatalogFile> = listOf(
        Qwen3TtsCatalogFile("legacy/talker_int4.tflite", "talker_int4.tflite",
            255998768, "e03df54e73ed1f88b2ae6d47bbf82dd64ea90a3620d753a0f3c8d6a8d60848db"),
        Qwen3TtsCatalogFile("legacy/mtp_fp32.tflite", "mtp_fp32.tflite",
            440526692, "7cc01b402637c850432ee9171c9cc9103813c0406b70c455ea8752833adf1f03"),
        Qwen3TtsCatalogFile("legacy/codec_decoder_fp32.tflite", "codec_decoder_fp32.tflite",
            456820324, "491e10c263c498d9f305ae655dbcce18455d54e44421480afe2d7da2b07da39d"),
        Qwen3TtsCatalogFile("legacy/tables/codec_embedding_fp32.npy", "codec_embedding_fp32.npy",
            12583040, "47fa9e30f98b1528fc9b332d314f22a32fa33e187509a4d3537f8b2c31199e39"),
        Qwen3TtsCatalogFile("legacy/tables/mtp_embeddings_fp16.npy", "mtp_embeddings_fp16.npy",
            62914688, "fea581b6a04f1cbec20b49511c36a00011411ccfba31f89b7571f82fe6b36706"),
        Qwen3TtsCatalogFile("legacy/tables/text_embedding_fp16.npy", "text_embedding_fp16.npy",
            622329984, "6fab9de0a8bc144aa3efefdacb5e8292b8499a9ae1d60fcee240bac528b7441e"),
        Qwen3TtsCatalogFile("legacy/tables/text_projection_fp32.npz", "text_projection_fp32.npz",
            25179078, "ebb0f6a7aaacdbc903e825e33480b2da4d4b71c43c90acb0e89988049c77c100"),
        Qwen3TtsCatalogFile("legacy/voices/demo_speaker.npy", "demo_speaker.npy",
            4224, "b1527f54f68f44ca98bfddcaa9dc0018deb2db62590c9d2699efabbd0dfc1c3c"),
        Qwen3TtsCatalogFile("legacy/vocab.json", "vocab.json",
            2776833, "ca10d7e9fb3ed18575dd1e277a2579c16d108e32f27439684afa0e10b1440910"),
        Qwen3TtsCatalogFile("legacy/merges.txt", "merges.txt",
            1671839, "599bab54075088774b1733fde865d5bd747cbcc7a547c5bc12610e874e26f5e3"),
    )

    val requiredFiles: List<String> get() = files.map { it.local }

    fun resolveFileUrl(remote: String): String =
        "https://huggingface.co/$modelId/resolve/$REVISION/$remote"

    fun remoteForLocal(local: String): String? =
        files.firstOrNull { it.local == local }?.remote

    fun downloadPairs(): List<Pair<String, String>> =
        files.map { resolveFileUrl(it.remote) to it.local }

    fun isVerified(directory: File): Boolean = verifyFiles(directory, files)

    /** Pure verification entry point for tiny fixtures and model bundles alike. */
    fun verifyFiles(directory: File, expectedFiles: List<Qwen3TtsCatalogFile>): Boolean =
        directory.isDirectory && expectedFiles.isNotEmpty() && expectedFiles.all { file ->
            val target = File(directory, file.local)
            target.isFile && ModelInstall.verifiedArtifactMatches(target, file.expected)
        }

    val sourceUrl: String get() = "https://huggingface.co/$modelId"
}

object Qwen3TtsCatalog {
    val ENTRIES: List<Qwen3TtsCatalogEntry> = listOf(
        Qwen3TtsCatalogEntry(
            displayName = "Qwen3-TTS-12Hz-0.6B-Base (Legacy LiteRT)",
            modelId = "litert-community/Qwen3-TTS-12Hz-0.6B-Base",
            description = "Original three-graph LiteRT CPU TTS host loop (not the new " +
                "LiteRT-LM .litertlm format). Ten pinned files, ~1.88 GB, " +
                "24 kHz, not realtime; Android inference still needs acceptance testing.",
            sizeBytes = 1_880_805_470L,
            license = "apache-2.0",
        ),
    )

    fun findByModelId(modelId: String): Qwen3TtsCatalogEntry? =
        ENTRIES.firstOrNull { it.modelId == modelId }
}
