package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Independent of persistence so callers keep their existing preference boundary. */
data class CompactToggleOption(
    val title: String,
    val description: String,
    val enabled: Boolean,
    val change: (Boolean) -> Unit,
)

@Composable
fun CompactToggleGrid(options: List<CompactToggleOption>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { item ->
                    Box(Modifier.weight(1f)) {
                        CompactToggleTile(item)
                    }
                }
                repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CompactToggleTile(item: CompactToggleOption) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    var showExplanation by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        if (item.enabled) colors.primaryContainer.copy(alpha = 0.34f)
        else colors.surfaceContainerLow,
        label = "setting-tile-background",
    )
    Surface(
        modifier = Modifier.fillMaxWidth().height(94.dp).combinedClickable(
            role = Role.Switch,
            onClick = { item.change(!item.enabled) },
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showExplanation = true
            },
            onLongClickLabel = "Setting details",
        ).semantics {
            stateDescription = if (item.enabled) "On" else "Off"
        },
        color = background,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (item.enabled) colors.primary.copy(alpha = 0.5f)
            else colors.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(11.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                item.title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                val trackColor by animateColorAsState(
                    if (item.enabled) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.22f),
                    label = "toggle-track",
                )
                val thumbOffset by animateDpAsState(
                    if (item.enabled) 19.dp else 3.dp,
                    animationSpec = spring(stiffness = 550f),
                    label = "toggle-thumb",
                )
                Surface(
                    modifier = Modifier.width(41.dp).height(22.dp),
                    shape = CircleShape,
                    color = trackColor,
                    border = BorderStroke(
                        1.dp,
                        if (item.enabled) colors.primary else colors.outline,
                    ),
                ) {
                    Box {
                        Surface(
                            modifier = Modifier.offset(x = thumbOffset, y = 3.dp).size(16.dp),
                            shape = CircleShape,
                            color = if (item.enabled) colors.onPrimary else colors.onSurfaceVariant,
                        ) {}
                    }
                }
            }
        }
    }
    if (showExplanation) {
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            title = { Text(item.title) },
            text = { Text(item.description) },
            confirmButton = {
                TextButton(onClick = { showExplanation = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}
