package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A miniature representation of the actual surface/accent hierarchy rather
 * than a random color wheel. Material You uses system colors when available.
 */
@Composable
fun ThemeChoiceCard(
    title: String,
    description: String,
    scheme: ColorScheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outer = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) outer.primaryContainer.copy(alpha = 0.35f) else outer.surfaceContainerLow,
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) outer.primary else outer.outlineVariant,
        ),
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ThemeMiniature(scheme)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = outer.onSurfaceVariant,
                )
                if (selected) {
                    Text(
                        text = "SELECTED",
                        style = MaterialTheme.typography.labelSmall,
                        color = outer.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeMiniature(scheme: ColorScheme) {
    Surface(
        modifier = Modifier.width(90.dp).height(86.dp),
        shape = RoundedCornerShape(12.dp),
        color = scheme.background,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(11.dp).clip(RoundedCornerShape(4.dp)).background(scheme.primary))
                Box(
                    Modifier.width(38.dp).height(4.dp)
                        .clip(RoundedCornerShape(3.dp)).background(scheme.onSurface.copy(alpha = 0.55f))
                )
            }
            Surface(
                color = scheme.surfaceContainerHigh,
                shape = RoundedCornerShape(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(scheme.primaryContainer))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.width(33.dp).height(3.dp).background(scheme.onSurface.copy(alpha = 0.65f), RoundedCornerShape(2.dp)))
                        Box(Modifier.width(23.dp).height(3.dp).background(scheme.onSurfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(2.dp)))
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(Modifier.width(29.dp).height(8.dp).background(scheme.primary, RoundedCornerShape(4.dp)))
            }
        }
    }
}
