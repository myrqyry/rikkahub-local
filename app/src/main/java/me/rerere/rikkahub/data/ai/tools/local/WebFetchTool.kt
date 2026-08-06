package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.net.hostIsBlockedLiteral
import me.rerere.rikkahub.data.ai.net.withEgressGuard
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException

private const val WEB_FETCH_TIMEOUT_MS = 30_000L
private const val WEB_FETCH_BODY_CAP = 8 * 1024  // 8 KB

/**
 * Lightweight HTTP GET/POST tool so workflows and agents can fetch a public URL without driving
 * the full browser or shelling out to Termux. The shared client is wrapped with an egress guard
 * for each call so loopback, private, link-local, multicast, CGNAT, and IPv6 unique-local targets
 * are refused across the initial request and every redirect.
 *
 * OkHttp's call timeout enforces the advertised 30-second bound on the complete blocking request,
 * including slow response reads. [withTimeoutOrNull] remains as a secondary coroutine-side bound.
 * The response body is capped at 8 KB and the cap is reported to the caller.
 */
fun webFetchTool(client: OkHttpClient): Tool = Tool(
    name = "web_fetch",
    description = """
        Fetch a public URL over HTTP(S). method is GET (default) or POST. Optionally pass headers
        (object of name->value) and a body string (POST only). Private, loopback, link-local, and
        other non-public network targets are refused, including redirect targets. Hard 30s timeout.
        The response body is capped at 8192 bytes; body_truncated=true when more data remained.
        Returns {status, ok, headers, body, body_truncated} or {error, detail, recovery}.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("url", buildJsonObject {
                    put("type", "string")
                    put("description", "The public http:// or https:// URL to fetch")
                })
                put("method", buildJsonObject {
                    put("type", "string")
                    put("description", "GET (default) or POST")
                })
                put("headers", buildJsonObject {
                    put("type", "object")
                    put("description", "Optional request headers as a name->value object")
                })
                put("body", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional request body string (POST only)")
                })
            },
            required = listOf("url"),
        )
    },
    execute = { input ->
        val obj = input.jsonObject
        val url = obj["url"]?.jsonPrimitive?.contentOrNull?.trim()
        if (url.isNullOrBlank()) {
            return@Tool fmTextPart(fmErrEnvelope("missing_url", "url is required"))
        }
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            return@Tool fmTextPart(
                buildJsonObject {
                    put("error", "bad_url")
                    put("detail", "url must start with http:// or https://")
                    put("recovery", "Pass an absolute http(s) URL.")
                }.toString(),
            )
        }

        // OkHttp bypasses custom DNS for a literal IP host, so reject unsafe literals before
        // building the request. The guarded network interceptor repeats this check after redirects.
        url.toHttpUrlOrNull()?.host?.let { host ->
            if (hostIsBlockedLiteral(host)) {
                return@Tool fmTextPart(
                    buildJsonObject {
                        put("error", "blocked_address")
                        put("detail", "blocked_private_address: $host")
                        put(
                            "recovery",
                            "This tool refuses private, loopback, link-local, and other non-public addresses. Use a public URL.",
                        )
                    }.toString(),
                )
            }
        }

        val method = obj["method"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase() ?: "GET"
        if (method != "GET" && method != "POST") {
            return@Tool fmTextPart(
                buildJsonObject {
                    put("error", "bad_method")
                    put("detail", "method must be GET or POST, got $method")
                    put("recovery", "Use method=GET or method=POST.")
                }.toString(),
            )
        }
        val bodyStr = obj["body"]?.jsonPrimitive?.contentOrNull

        val request = try {
            val builder = Request.Builder().url(url)
            (obj["headers"] as? kotlinx.serialization.json.JsonObject)?.forEach { (name, value) ->
                value.jsonPrimitive.contentOrNull?.let { builder.header(name, it) }
            }
            if (method == "POST") {
                builder.post((bodyStr ?: "").toRequestBody())
            } else {
                builder.get()
            }
            builder.build()
        } catch (error: IllegalArgumentException) {
            return@Tool fmTextPart(
                buildJsonObject {
                    put("error", "bad_request")
                    put("detail", error.message ?: "Could not build request")
                    put("recovery", "Check the URL and header names for invalid characters.")
                }.toString(),
            )
        }

        val guardedClient = client.withEgressGuard()
        val result = withTimeoutOrNull(WEB_FETCH_TIMEOUT_MS) {
            try {
                guardedClient.newCall(request).execute().use { response ->
                    // Read CAP+1 bytes: the extra byte detects truncation without buffering the
                    // rest of a potentially enormous or unbounded response.
                    val (raw, truncated) = readBounded(
                        response.body.byteStream(),
                        WEB_FETCH_BODY_CAP,
                    )
                    val bodyText = String(
                        raw,
                        0,
                        minOf(raw.size, WEB_FETCH_BODY_CAP),
                        Charsets.UTF_8,
                    )
                    buildJsonObject {
                        put("status", response.code)
                        put("ok", response.isSuccessful)
                        put("headers", buildJsonObject {
                            response.headers.forEach { (name, value) -> put(name, value) }
                        })
                        put("body", bodyText)
                        put("body_truncated", truncated)
                    }.toString()
                }
            } catch (_: InterruptedIOException) {
                buildJsonObject {
                    put("error", "timeout")
                    put("detail", "Request exceeded the 30s limit.")
                    put(
                        "recovery",
                        "The host is slow or unreachable; try a smaller request or a different URL.",
                    )
                }.toString()
            } catch (error: IOException) {
                val blocked = error.message?.contains("blocked_private_address") == true
                buildJsonObject {
                    put("error", if (blocked) "blocked_address" else "network_error")
                    put("detail", error.message ?: error::class.java.simpleName)
                    put(
                        "recovery",
                        if (blocked) {
                            "This tool refuses private, loopback, link-local, and other non-public addresses. Use a public URL."
                        } else {
                            "Check connectivity and that the host is reachable, then retry."
                        },
                    )
                }.toString()
            }
        } ?: buildJsonObject {
            put("error", "timeout")
            put("detail", "Request exceeded the 30s limit.")
            put(
                "recovery",
                "The host is slow or unreachable; try a smaller request or a different URL.",
            )
        }.toString()

        fmTextPart(result)
    },
)

/**
 * Reads at most [cap] bytes plus one probe byte. The probe indicates that additional content
 * remained while bounding memory regardless of Content-Length or a missing/incorrect header.
 */
internal fun readBounded(ins: InputStream, cap: Int): Pair<ByteArray, Boolean> {
    val out = ByteArrayOutputStream(minOf(cap, 8 * 1024))
    val buffer = ByteArray(8192)
    val limit = cap.toLong() + 1
    var total = 0L
    while (total < limit) {
        val wanted = minOf(buffer.size.toLong(), limit - total).toInt()
        val read = ins.read(buffer, 0, wanted)
        if (read < 0) break
        out.write(buffer, 0, read)
        total += read
    }
    val bytes = out.toByteArray()
    return bytes to (bytes.size > cap)
}
