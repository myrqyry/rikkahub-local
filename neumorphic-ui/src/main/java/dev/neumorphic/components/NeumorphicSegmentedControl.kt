package dev.neumorphic.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neumorphic.LocalNeumorphicColors
import dev.neumorphic.NeumorphicColors
import dev.neumorphic.NeumorphicShape
import dev.neumorphic.haptics.rememberNeumorphicHaptics
import dev.neumorphic.neumorphicInset
import dev.neumorphic.neumorphicRaised

/**
 * Reusable Neumorphic Segmented Control / Tabs matching image.png top-right (Tab 1, Tab 2, Tab 3).
 */
@Composable
fun NeumorphicSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 46.dp,
    cornerRadius: Dp = 14.dp,
    enabled: Boolean = true,
    colors: NeumorphicColors = LocalNeumorphicColors.current,
    testTag: String = "neumorphic_segmented_control"
) {
    if (items.isEmpty()) return
    val haptics = rememberNeumorphicHaptics()

    val trackShape = NeumorphicShape.Rounded(cornerRadius)
    val composeShape = RoundedCornerShape(cornerRadius)

    BoxWithConstraints(
        modifier = modifier
            .height(height)
            .clip(composeShape)
            .background(colors.surface)
            .neumorphicInset(colors = colors, shape = trackShape, depth = 3.dp)
            .padding(4.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .semantics { role = Role.Tab }
            .testTag(testTag)
    ) {
        val totalWidth = maxWidth
        val itemWidth = totalWidth / items.size

        val animatedIndex by animateFloatAsState(
            targetValue = selectedIndex.toFloat(),
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
            label = "tabIndicatorPos"
        )

        // Sliding Active Pill Indicator
        val activePillShape = NeumorphicShape.Rounded(cornerRadius - 2.dp)
        val activeComposeShape = RoundedCornerShape(cornerRadius - 2.dp)

        Box(
            modifier = Modifier
                .offset(x = itemWidth * animatedIndex)
                .width(itemWidth)
                .fillMaxHeight()
                .neumorphicRaised(colors = colors, shape = activePillShape, elevation = 3.dp)
                .clip(activeComposeShape)
                .background(colors.surface)
        )

        // Tab Labels
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, title ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (index != selectedIndex) {
                                    haptics.select()
                                }
                                onItemSelected(index)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) colors.accentPrimary else colors.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
