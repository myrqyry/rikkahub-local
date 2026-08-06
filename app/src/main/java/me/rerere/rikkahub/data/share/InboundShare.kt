package me.rerere.rikkahub.data.share

import android.content.Intent
import android.net.Uri

sealed interface InboundSharePayload {
    data class Text(val text: String) : InboundSharePayload
    data class Url(val url: String, val accompanyingText: String? = null) : InboundSharePayload
    data class File(
        val uri: Uri,
        val mimeType: String?,
        val displayName: String?,
        val accompanyingText: String? = null,
    ) : InboundSharePayload
}

/** Pure, Android-free classification result so the core logic is unit-testable on the JVM. */
internal sealed interface NormalizedShare {
    data class Text(val text: String) : NormalizedShare
    data class Url(val url: String, val accompanyingText: String?) : NormalizedShare
    data class File(
        val uri: String,
        val mimeType: String?,
        val accompanyingText: String?,
    ) : NormalizedShare
}

object InboundShareNormalizer {
    private fun isUrl(text: String): Boolean =
        text.startsWith("https://") || text.startsWith("http://")

    /**
     * Pure core: no android.* dependencies. `stream` is the raw EXTRA_STREAM
     * value (content:// uri string or null). Returns null when nothing shareable
     * is present or when the stream is not a content:// uri.
     */
    internal fun classify(action: String?, rawText: String?, stream: String?): NormalizedShare? {
        val text = rawText?.takeIf { it.isNotBlank() }
        if (stream != null) {
            if (!stream.startsWith("content:")) return null
            return NormalizedShare.File(
                uri = stream,
                mimeType = null,
                accompanyingText = text,
            )
        }
        text ?: return null
        return if (isUrl(text)) NormalizedShare.Url(text, null) else NormalizedShare.Text(text)
    }

    fun normalize(intent: Intent): InboundSharePayload? {
        val action = intent.action ?: return null
        val rawText = when (action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            else -> intent.getStringExtra(Intent.EXTRA_TEXT)
        }
        val streamUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        return when (val normalized = classify(action, rawText, streamUri?.toString())) {
            is NormalizedShare.Text -> InboundSharePayload.Text(normalized.text)
            is NormalizedShare.Url -> InboundSharePayload.Url(normalized.url, normalized.accompanyingText)
            is NormalizedShare.File -> InboundSharePayload.File(
                uri = Uri.parse(normalized.uri),
                mimeType = intent.type,
                displayName = null,
                accompanyingText = normalized.accompanyingText,
            )
            null -> null
        }
    }
}
