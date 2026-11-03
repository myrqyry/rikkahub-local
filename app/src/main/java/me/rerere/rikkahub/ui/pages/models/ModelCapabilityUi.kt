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
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.icons.MingcuteIcons
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.modelregistry.ModelCapability
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelLifecycle
import me.rerere.rikkahub.data.modelregistry.ModelSource

enum class ModelInventoryStatus {
    PROVIDER_DISABLED,
    CONNECTION_UNAVAILABLE,
    NOT_READY,
}

fun ModelDescriptor.inventoryStatus(): ModelInventoryStatus? = when {
    !providerEnabled -> ModelInventoryStatus.PROVIDER_DISABLED
    source is ModelSource.Cloud && !connected -> ModelInventoryStatus.CONNECTION_UNAVAILABLE
    source is ModelSource.Local && lifecycle != ModelLifecycle.READY -> ModelInventoryStatus.NOT_READY
    else -> null
}

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

@get:DrawableRes
val ModelCapability.iconRes: Int
    get() = when (this) {
        ModelCapability.CHAT -> MingcuteIcons.AiChat01Res
        ModelCapability.REASONING -> MingcuteIcons.AiBrain01Res
        ModelCapability.TOOLS -> MingcuteIcons.ToolsRes
        ModelCapability.VISION -> MingcuteIcons.EyeRes
        ModelCapability.OCR -> MingcuteIcons.AiScanRes
        ModelCapability.DOCUMENT_ANALYSIS -> MingcuteIcons.DocumentValidationRes
        ModelCapability.IMAGE_GENERATION -> MingcuteIcons.AiImageRes
        ModelCapability.IMAGE_EDITING -> MingcuteIcons.AiEditingRes
        ModelCapability.TEXT_TO_SPEECH -> MingcuteIcons.Speaker01Res
        ModelCapability.SPEECH_TO_TEXT -> MingcuteIcons.AiMicRes
        ModelCapability.AUDIO_UNDERSTANDING -> MingcuteIcons.AudioWave01Res
        ModelCapability.EMBEDDINGS -> MingcuteIcons.Database01Res
        ModelCapability.RERANKING -> MingcuteIcons.Sorting01Res
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
                painter = painterResource(capability.iconRes),
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
