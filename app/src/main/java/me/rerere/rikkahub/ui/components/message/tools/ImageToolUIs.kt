package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Image03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.tools.image.ImageToolResult
import me.rerere.rikkahub.ui.components.richtext.ZoomableAsyncImage
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.context.LocalNavController

/**
 * 聊天内四个图片工具 (generate_image / edit_image / analyze_image / extract_text_from_image)
 * 的结果卡片渲染器.
 *
 * [ImageToolResult] 是规范的工具结果契约: 工具把 JSON 信封写入 Text 部件, 渲染器直接解码
 * [ToolUIContext.content] 得到 artifacts. 解码失败 / schemaVersion 不兼容 / 执行失败 /
 * 无产物时回退默认渲染, 绝不解析展示文本或推断字段.
 *
 * 每个工具名对应一个实例, 在 [ToolUIRegistry] 中按 [ImageToolCatalog.TOOL_NAMES] 注册.
 */

/**
 * 解码 [ToolUIContext.content] 中的 [ImageToolResult] 信封. 纯函数, 供渲染器与 JVM 测试复用.
 */
internal fun decodeImageToolResult(content: JsonElement?, json: Json): ImageToolResult? =
    content?.let { runCatching { json.decodeFromJsonElement<ImageToolResult>(it) }.getOrNull() }

/**
 * 图片卡片是否可渲染: 解码成功且 schemaVersion 兼容且执行成功且有产物.
 * 不满足时渲染器回退默认工具渲染 (见 [ImageToolCardRenderer.Preview]).
 */
internal fun isImageToolResultRenderable(result: ImageToolResult?): Boolean =
    result != null && result.schemaVersion <= 1 && result.success && result.artifacts.isNotEmpty()

class ImageToolCardRenderer(
    override val toolName: String,
) : ToolUIRenderer {

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.Image03

    override fun hasSummary(context: ToolUIContext): Boolean = false

    @Composable
    override fun Summary(context: ToolUIContext) {
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val result = remember(context) { decodeImageToolResult(context.content, imageToolResultJson) }
        if (result == null || !isImageToolResultRenderable(result)) {
            // 解码失败 / schemaVersion 不兼容 / 执行失败 / 无产物: 回退默认工具渲染, 绝不崩溃
            DefaultToolPreview(context = context, headerActions = { onDismissRequest() })
            return
        }
        val artifacts = result.artifacts

        val navController = LocalNavController.current
        var showFullImage by remember { mutableStateOf(false) }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(artifacts) { artifact ->
                        ZoomableAsyncImage(
                            model = artifact.uri,
                            contentDescription = artifact.path,
                            modifier = Modifier.size(120.dp),
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { showFullImage = true }) {
                        Text(stringResource(R.string.image_tool_open))
                    }
                    // ImageGenPage 目前不支持初始参考图参数, "用作参考"/"编辑" 都直接打开图片工作室
                    TextButton(onClick = { navController.navigate(Screen.ImageGen) }) {
                        Text(stringResource(R.string.image_tool_use_as_reference))
                    }
                    TextButton(onClick = { navController.navigate(Screen.ImageGen) }) {
                        Text(stringResource(R.string.image_tool_edit))
                    }
                    TextButton(onClick = { navController.navigate(Screen.ImageGen) }) {
                        Text(stringResource(R.string.image_tool_open_studio))
                    }
                }
            }
        }
        if (showFullImage) {
            val firstUri = artifacts.firstOrNull()?.uri
            if (firstUri != null) {
                ImagePreviewDialog(images = listOf(firstUri)) {
                    showFullImage = false
                }
            }
        }
    }
}

private val imageToolResultJson = Json { ignoreUnknownKeys = true; explicitNulls = false }
