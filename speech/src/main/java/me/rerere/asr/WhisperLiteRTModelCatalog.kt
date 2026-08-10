package me.rerere.asr

data class WhisperLiteRTModelEntry(
    val id: String,
    val displayName: String,
    val filename: String,
    val description: String,
    val sizeBytes: Long?,
    val targetHardware: String?,
    val sourceUrl: String,
    val license: String,
) {
    val downloadUrl: String
        get() = "$sourceUrl/resolve/main/$filename"
}

object WhisperLiteRTModelCatalog {
    const val CUSTOM_ID = "custom"

    private const val BASE_SOURCE = "https://huggingface.co/litert-community/whisper-base"
    private const val TINY_SOURCE = "https://huggingface.co/litert-community/whisper-tiny"

    val entries: List<WhisperLiteRTModelEntry> = buildList {
        add(
            WhisperLiteRTModelEntry(
                id = "whisper-base",
                displayName = "Whisper Base",
                filename = "whisper_base_30s_f32.tflite",
                description = "Portable Whisper Base model",
                sizeBytes = 480_000_000L,
                targetHardware = "Portable CPU",
                sourceUrl = BASE_SOURCE,
                license = "apache-2.0",
            ),
        )
        add(
            WhisperLiteRTModelEntry(
                id = "whisper-tiny",
                displayName = "Whisper Tiny",
                filename = "whisper_tiny_30s_f32.tflite",
                description = "Portable Whisper Tiny model",
                sizeBytes = 151_000_000L,
                targetHardware = "Portable CPU",
                sourceUrl = TINY_SOURCE,
                license = "apache-2.0",
            ),
        )
        add(
            WhisperLiteRTModelEntry(
                id = "whisper-tiny-i8",
                displayName = "Whisper Tiny (int8)",
                filename = "whisper_tiny_30s_i8.tflite",
                description = "Smaller quantized Whisper Tiny model",
                sizeBytes = 41_100_000L,
                targetHardware = "Portable CPU",
                sourceUrl = TINY_SOURCE,
                license = "apache-2.0",
            ),
        )
        addAll(
            listOf(
                device("Qualcomm", "SA8255", 120_000_000L),
                device("Qualcomm", "SA8295", 119_000_000L),
                device("Qualcomm", "SM8450", 119_000_000L),
                device("Qualcomm", "SM8550", 120_000_000L),
                device("Qualcomm", "SM8650", 120_000_000L),
                device("Qualcomm", "SM8750", 120_000_000L),
                device("Qualcomm", "SM8850", 120_000_000L),
                device("MediaTek", "MT6877", 158_000_000L),
                device("MediaTek", "MT6878", 156_000_000L),
                device("MediaTek", "MT6879", 158_000_000L),
                device("MediaTek", "MT6897", 156_000_000L),
                device("MediaTek", "MT6983", 156_000_000L),
                device("MediaTek", "MT6985", 156_000_000L),
                device("MediaTek", "MT6989", 156_000_000L),
                device("MediaTek", "MT6991", 156_000_000L),
                device("MediaTek", "MT6993", 156_000_000L),
                device("MediaTek", "MT8171", 156_000_000L),
                device("MediaTek", "MT8188", 156_000_000L),
                device("MediaTek", "MT8189", 156_000_000L),
            ),
        )
    }

    fun findById(id: String): WhisperLiteRTModelEntry? =
        entries.firstOrNull { it.id == id }

    fun findByFilename(filename: String): WhisperLiteRTModelEntry? =
        entries.firstOrNull { it.filename == filename }

    private fun device(vendor: String, chipset: String, sizeBytes: Long) =
        WhisperLiteRTModelEntry(
            id = "whisper-tiny-${vendor.lowercase()}-${chipset.lowercase()}",
            displayName = "Whisper Tiny ($vendor $chipset)",
            filename = "whisper_tiny_30s_f32_${vendor}_$chipset.tflite",
            description = "Device-optimized Whisper Tiny model",
            sizeBytes = sizeBytes,
            targetHardware = "$vendor $chipset",
            sourceUrl = TINY_SOURCE,
            license = "apache-2.0",
        )
}
