package dev.neumorphic.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.neumorphic.LocalNeumorphicColors

/**
 * Inset/engraved text — game-controller-style debossed labels.
 *
 * Draws the highlight pass 1.dp below first, then the dark glyph on top, so
 * letterforms read as carved into the surface. Both colors come from the
 * palette, so the carve tracks light/dark mode and accent tuning.
 *
 * // ponytail: two-pass draw, no blur — per-glyph BlurMaskFilter is GPU-hostile.
 */
@Composable
fun NeumorphicEngravedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalNeumorphicColors.current.textPrimary,
    highlight: Color = LocalNeumorphicColors.current.lightShadow,
) {
    Box(modifier) {
        Text(
            text = text,
            style = style,
            color = highlight,
            // Highlight is a visual echo — hide it so TalkBack announces one title.
            modifier = Modifier.offset(y = 1.dp).clearAndSetSemantics {},
        )
        Text(
            text = text,
            style = style,
            color = color,
        )
    }
}
