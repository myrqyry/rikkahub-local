package me.rerere.rikkahub.ui.pages.models.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.pages.models.ModelMemoryFit

internal fun ModelMemoryFit.labelRes(): Int = when (this) {
    is ModelMemoryFit.FitsNow -> R.string.models_memory_fit_now
    is ModelMemoryFit.NeedsMoreMemory -> R.string.models_memory_needs_more
    ModelMemoryFit.Checking -> R.string.models_memory_checking
    ModelMemoryFit.Unavailable -> R.string.models_memory_unavailable
}

internal fun ModelMemoryFit.labelArgs(): Array<Any> = when (this) {
    is ModelMemoryFit.NeedsMoreMemory -> arrayOf(
        requiredFreeBytes / 1_000_000,
        availMemBytes / 1_000_000,
    )
    else -> emptyArray()
}

@Composable
fun ModelMemoryFitBadge(
    fit: ModelMemoryFit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(fit.labelRes(), *fit.labelArgs())
    val (icon, color) = when (fit) {
        is ModelMemoryFit.FitsNow -> HugeIcons.CheckmarkCircle02 to MaterialTheme.colorScheme.primary
        is ModelMemoryFit.NeedsMoreMemory -> HugeIcons.Alert01 to MaterialTheme.colorScheme.error
        ModelMemoryFit.Checking -> HugeIcons.Clock02 to MaterialTheme.colorScheme.onSurfaceVariant
        ModelMemoryFit.Unavailable -> HugeIcons.Alert01 to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = color)
        Text(label, color = color, style = MaterialTheme.typography.bodySmall)
    }
}
