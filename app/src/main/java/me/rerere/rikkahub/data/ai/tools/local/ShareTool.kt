package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.ToolInvocationContext
import me.rerere.rikkahub.data.share.AndroidShareService
import me.rerere.rikkahub.data.share.ShareOutcome
import me.rerere.rikkahub.data.share.ShareableArtifact

fun shareTool(
    context: Context,
    invocationContext: ToolInvocationContext = ToolInvocationContext.EMPTY,
    streamer: InteractiveToolStreamer = InteractiveToolStreamer.NoOp,
    shareService: AndroidShareService? = null,
): Tool = Tool(
    name = "share",
    description = """
        Open the system share sheet so the user can send text, a URL, or a previously
        generated image artifact to another app (messages, email, etc.). Provide at least
        one of text or url, or an artifact_ref pointing to an image gallery artifact.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "Text content to share")
                })
                put("url", buildJsonObject {
                    put("type", "string")
                    put("description", "URL to share")
                })
                put("subject", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional subject (e.g., for email)")
                })
                put("artifact_ref", buildJsonObject {
                    put("type", "string")
                    put("description", "Reference to an image gallery artifact (img_<id> or <id>) to share")
                })
                put("chooser_title", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional title for the system share sheet")
                })
            }
        )
    },
    needsApproval = { args ->
        val artifactRef = args.jsonObject["artifact_ref"]?.jsonPrimitive?.contentOrNull
        !artifactRef.isNullOrEmpty()
    },
    execute = {
        wakeScreenIfNeeded(context)
        val params = it.jsonObject
        val text = params["text"]?.jsonPrimitive?.contentOrNull?.takeIf { s -> s.isNotEmpty() }
        val url = params["url"]?.jsonPrimitive?.contentOrNull?.takeIf { s -> s.isNotEmpty() }
        val subject = params["subject"]?.jsonPrimitive?.contentOrNull
        val artifactRef = params["artifact_ref"]?.jsonPrimitive?.contentOrNull
        val chooserTitle = params["chooser_title"]?.jsonPrimitive?.contentOrNull

        if (text == null && url == null && artifactRef.isNullOrEmpty()) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject { put("error", "provide at least one of text or url") }.toString()
                )
            )
        }

        val shareLabel = listOfNotNull(text, url).joinToString("\n").take(50)
        val outcome: ShareOutcome = if (!artifactRef.isNullOrEmpty()) {
            if (shareService == null) {
                return@Tool listOf(
                    UIMessagePart.Text(
                        buildJsonObject { put("error", "share service unavailable") }.toString()
                    )
                )
            }
            val artifact = shareService.resolve(artifactRef)
            if (artifact == null) {
                return@Tool listOf(
                    UIMessagePart.Text(
                        buildJsonObject { put("error", "artifact not found: $artifactRef") }.toString()
                    )
                )
            }
            shareService.shareArtifact(artifact, text, subject)
        } else {
            shareService?.shareText(url, text, subject) ?: ShareOutcome.Unsupported("share service unavailable")
        }

        when (outcome) {
            is ShareOutcome.ChooserOpened -> {
                streamer.streamIfHeadless(invocationContext, "Share: $shareLabel")
                listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put("status", "chooser_opened")
                            put("artifact_id", outcome.artifactId)
                            put("mime_type", outcome.mimeType)
                        }.toString()
                    )
                )
            }
            is ShareOutcome.Unsupported -> listOf(
                UIMessagePart.Text(
                    buildJsonObject { put("error", outcome.reason) }.toString()
                )
            )
        }
    }
)
