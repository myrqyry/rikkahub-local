package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.neumorphic.NeumorphicColors
import dev.neumorphic.NeumorphicInsetSurface
import dev.neumorphic.NeumorphicElevation
import dev.neumorphic.NeumorphicShape
import dev.neumorphic.NeumorphicRaisedSurface
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.icons.MingcuteIcons as HugeIcons
import me.rerere.rikkahub.ui.theme.rememberRikkaNeumorphicColors
import me.rerere.rikkahub.utils.navigateToChatPage

/** Persistent shortcuts beside the independently scrolling conversation history.
 * Matches OpenCode's left rail hierarchy without desktop-only hover behavior.
 */
@Composable
internal fun DrawerNavigationRail(navController: Navigator) {
    val colors = rememberRikkaNeumorphicColors()
    var showMore by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DrawerRailButton(
            icon = HugeIcons.PencilEdit01,
            label = stringResource(R.string.chat_page_new_message),
            colors = colors,
            onClick = { navigateToChatPage(navController) },
        )
        DrawerRailButton(
            icon = HugeIcons.Search01,
            label = stringResource(R.string.chat_page_search_chats),
            colors = colors,
            onClick = { navController.navigate(Screen.MessageSearch) },
        )
        DrawerRailButton(
            icon = HugeIcons.TransactionHistory,
            label = stringResource(R.string.chat_page_history),
            colors = colors,
            onClick = { navController.navigate(Screen.History) },
        )
        Spacer(Modifier.weight(1f))
        DrawerRailButton(
            icon = HugeIcons.Cpu,
            label = stringResource(R.string.models_title),
            colors = colors,
            onClick = { navController.navigate(Screen.Models()) },
        )
        DrawerRailButton(
            icon = HugeIcons.Image02,
            label = stringResource(R.string.chat_drawer_images),
            colors = colors,
            onClick = { navController.navigate(Screen.ImageGen) },
        )
        DrawerRailButton(
            icon = HugeIcons.Settings03,
            label = stringResource(R.string.settings),
            colors = colors,
            onClick = { navController.navigate(Screen.Setting) },
        )
        Box {
            DrawerRailButton(
                icon = HugeIcons.MoreVertical,
                label = stringResource(R.string.chat_drawer_more),
                colors = colors,
                onClick = { showMore = true },
            )
            DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                @Composable
                fun link(icon: ImageVector, label: String, destination: Screen) {
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = { Icon(icon, contentDescription = null) },
                        onClick = {
                            showMore = false
                            navController.navigate(destination)
                        },
                    )
                }
                link(HugeIcons.LookTop, stringResource(R.string.assistant_page_title), Screen.Assistant)
                link(HugeIcons.Wrench01, stringResource(R.string.extensions_page_title), Screen.Extensions)
                link(HugeIcons.InLove, stringResource(R.string.favorite_page_title), Screen.Favorite)
                link(HugeIcons.ChartColumn, stringResource(R.string.chat_drawer_statistics), Screen.Stats)
            }
        }
    }
}

/** Icon-only visual, but retains readable accessibility labels and a 44dp tap target. */
@Composable
private fun DrawerRailButton(
    icon: ImageVector,
    label: String,
    colors: NeumorphicColors,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "drawer rail press",
    )
    val iconContent: @Composable BoxScope.() -> Unit = {
        Box(
            modifier = Modifier
                .size(44.dp)
                .semantics { contentDescription = label }
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClickLabel = label,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val modifier = Modifier
        .size(44.dp)
        .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
    if (pressed) {
        NeumorphicInsetSurface(
            modifier = modifier,
            colors = colors,
            shape = NeumorphicShape.Rounded(15.dp),
            elevation = NeumorphicElevation.Subtle,
            backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
            content = iconContent,
        )
    } else {
        NeumorphicRaisedSurface(
            modifier = modifier,
            colors = colors,
            shape = NeumorphicShape.Rounded(15.dp),
            elevation = NeumorphicElevation.Subtle,
            backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
            content = iconContent,
        )
    }
}
