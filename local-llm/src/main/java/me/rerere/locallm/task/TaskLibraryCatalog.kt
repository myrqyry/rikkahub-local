package me.rerere.locallm.task

enum class TaskKind {
    IMAGE_CLASSIFICATION,
    OBJECT_DETECTION,
    OCR,
    AUDIO_CLASSIFICATION,
}

data class TaskLibraryCatalogEntry(
    val displayName: String,
    val modelId: String,
    val modelFiles: List<String>,
    val description: String,
    val sizeBytes: Long,
    val minDeviceMemoryGb: Int,
    val taskKind: TaskKind,
    val tags: List<String> = emptyList(),
    val sourceUrl: String = "https://huggingface.co/$modelId",
) {
    fun resolveUrl(file: String) = "https://huggingface.co/$modelId/resolve/main/$file"
}

// ponytail: link-only catalog, mirrors LiteRtCatalog. Users copy the URL or import the file;
// the app never downloads. Every entry below is a verified reachable HF repo+file.
object TaskLibraryCatalog {
    val ENTRIES: List<TaskLibraryCatalogEntry> = listOf(
        TaskLibraryCatalogEntry(
            displayName = "MobileNet-v2",
            modelId = "litert-community/MobileNet-v2",
            modelFiles = listOf("mobilenet_v2.tflite"),
            description = "ImageNet-1k image classifier (1000 classes). Fastest on-device classification.",
            sizeBytes = 14_352_200L,
            minDeviceMemoryGb = 2,
            taskKind = TaskKind.IMAGE_CLASSIFICATION,
            tags = listOf("classification", "imagenet"),
        ),
        TaskLibraryCatalogEntry(
            displayName = "EfficientNet-B0",
            modelId = "litert-community/efficientnet_b0",
            modelFiles = listOf("efficientnet_b0.tflite"),
            description = "ImageNet-1k image classifier, better accuracy/FLOP tradeoff than MobileNet-v2.",
            sizeBytes = 21_344_120L,
            minDeviceMemoryGb = 2,
            taskKind = TaskKind.IMAGE_CLASSIFICATION,
            tags = listOf("classification", "imagenet"),
        ),
        TaskLibraryCatalogEntry(
            displayName = "EfficientDet-Lite0",
            modelId = "litert-community/efficientdet",
            modelFiles = listOf("efficientdet_lite0_detection.tflite"),
            description = "COCO object detection (80 classes) with bounding boxes. Good for photo tagging.",
            sizeBytes = 5_000_000L,
            minDeviceMemoryGb = 2,
            taskKind = TaskKind.OBJECT_DETECTION,
            tags = listOf("detection", "coco"),
        ),
        TaskLibraryCatalogEntry(
            displayName = "PP-OCRv5",
            modelId = "litert-community/PP-OCRv5-LiteRT",
            modelFiles = listOf("ppocr_det_fp16.tflite", "ppocr_rec_fp16.tflite"),
            description = "On-device OCR. Needs both the detection and recognition graphs. Replaces cloud OCR for private images.",
            sizeBytes = 15_000_000L,
            minDeviceMemoryGb = 2,
            taskKind = TaskKind.OCR,
            tags = listOf("ocr", "text-recognition"),
        ),
        TaskLibraryCatalogEntry(
            displayName = "Wav2Vec2 Keyword Spotting",
            modelId = "litert-community/wav2vec2-keyword-spotting",
            modelFiles = listOf("w2v2_frontend_fp16.tflite", "w2v2_head_fp16.tflite"),
            description = "Audio classifier for keyword spotting (e.g. 'hey'/'wake word'). Frontend + head graphs.",
            sizeBytes = 20_000_000L,
            minDeviceMemoryGb = 2,
            taskKind = TaskKind.AUDIO_CLASSIFICATION,
            tags = listOf("audio", "kws"),
        ),
        TaskLibraryCatalogEntry(
            displayName = "PANNs CNN14 AudioSet",
            modelId = "litert-community/PANNs-CNN14-AudioSet-LiteRT",
            modelFiles = listOf("cnn14_audioset_fp16.tflite"),
            description = "Audio event classifier across 527 AudioSet classes (speech, music, alarms, animals...).",
            sizeBytes = 38_000_000L,
            minDeviceMemoryGb = 2,
            taskKind = TaskKind.AUDIO_CLASSIFICATION,
            tags = listOf("audio", "eventset"),
        ),
    )

    fun findByTaskKind(kind: TaskKind) = ENTRIES.filter { it.taskKind == kind }
}
