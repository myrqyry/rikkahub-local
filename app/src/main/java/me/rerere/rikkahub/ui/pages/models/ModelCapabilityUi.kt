package me.rerere.rikkahub.ui.pages.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiBrain01
import me.rerere.hugeicons.stroke.AiChat01
import me.rerere.hugeicons.stroke.AiEditing
import me.rerere.hugeicons.stroke.AiImage
import me.rerere.hugeicons.stroke.AiMic
import me.rerere.hugeicons.stroke.AiScan
import me.rerere.hugeicons.stroke.AudioWave01
import me.rerere.hugeicons.stroke.Database01
import me.rerere.hugeicons.stroke.DocumentValidation
import me.rerere.hugeicons.stroke.Eye
import me.rerere.hugeicons.stroke.Sorting01
import me.rerere.hugeicons.stroke.Speaker01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.modelregistry.ModelCapability
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle

val ModelLifecycle.labelRes: Int
    get() = when (this) {
        ModelLifecycle.AVAILABLE -> R.string.models_lifecycle_available
        ModelLifecycle.DOWNLOADING -> R.string.models_lifecycle_downloading
        ModelLifecycle.INSTALLED -> R.string.models_lifecycle_installed
        ModelLifecycle.VERIFYING -> R.string.models_lifecycle_verifying
        ModelLifecycle.READY -> R.string.models_lifecycle_ready
        ModelLifecycle.INCOMPATIBLE -> R.string.models_lifecycle_incompatible
        ModelLifecycle.ERROR -> R.string.models_lifecycle_error
    }

val ModelCapability.labelRes: Int
    get() = when (this) {
        ModelCapability.CHAT -> R.string.models_capability_chat
        ModelCapability.REASONING -> R.string.models_capability_reasoning
        ModelCapability.TOOLS -> R.string.models_capability_tools
        ModelCapability.VISION -> R.string.models_capability_vision
        ModelCapability.OCR -> R.string.models_capability_ocr
        ModelCapability.DOCUMENT_ANALYSIS -> R.string.models_capability_document_analysis
        ModelCapability.IMAGE_GENERATION -> R.string.models_capability_image_generation
        ModelCapability.IMAGE_EDITING -> R.string.models_capability_image_editing
        ModelCapability.TEXT_TO_SPEECH -> R.string.models_capability_text_to_speech
        ModelCapability.SPEECH_TO_TEXT -> R.string.models_capability_speech_to_text
        ModelCapability.AUDIO_UNDERSTANDING -> R.string.models_capability_audio_understanding
        ModelCapability.EMBEDDINGS -> R.string.models_capability_embeddings
        ModelCapability.RERANKING -> R.string.models_capability_reranking
    }

val ModelCapability.icon: ImageVector
    get() = when (this) {
        ModelCapability.CHAT -> HugeIcons.AiChat01
        ModelCapability.REASONING -> HugeIcons.AiBrain01
        ModelCapability.TOOLS -> HugeIcons.Tools
        ModelCapability.VISION -> HugeIcons.Eye
        ModelCapability.OCR -> HugeIcons.AiScan
        ModelCapability.DOCUMENT_ANALYSIS -> HugeIcons.DocumentValidation
        ModelCapability.IMAGE_GENERATION -> HugeIcons.AiImage
        ModelCapability.IMAGE_EDITING -> HugeIcons.AiEditing
        ModelCapability.TEXT_TO_SPEECH -> HugeIcons.Speaker01
        ModelCapability.SPEECH_TO_TEXT -> HugeIcons.AiMic
        ModelCapability.AUDIO_UNDERSTANDING -> HugeIcons.AudioWave01
        ModelCapability.EMBEDDINGS -> HugeIcons.Database01
        ModelCapability.RERANKING -> HugeIcons.Sorting01
    }

@Composable
fun ModelCapabilityRow(capabilities: Collection<ModelCapability>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        capabilities.sortedBy { it.name }.forEach { capability ->
            Icon(
                imageVector = capability.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(capability.labelRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
