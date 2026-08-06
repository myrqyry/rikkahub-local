package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.AgentTurnTracker
import me.rerere.rikkahub.data.ai.tools.ToolInvocationContext
import me.rerere.rikkahub.service.RikkaAccessibilityService

private suspend fun waitForForegroundPackage(
    expectedPkg: String,
    timeoutMs: Long = 2500,
    stepMs: Long = 150,
): String? {
    val deadline = System.currentTimeMillis() + timeoutMs
    var current: String? = null
    while (System.currentTimeMillis() < deadline) {
        val service = RikkaAccessibilityService.instance ?: return null
        current = service.rootInActiveWindow?.packageName?.toString()
        if (current == expectedPkg) return current
        delay(stepMs)
    }
    return current
}

private fun resolveActivityName(packageName: String, rawActivityName: String): String =
    if (rawActivityName.startsWith('.')) packageName + rawActivityName else rawActivityName

/**
 * Opens either an app's normal launcher entry point or one explicit exported activity.
 *
 * Supplying [activity_name] lets the agent jump directly to a settings screen, share target,
 * or other exported activity discovered through [listInstalledAppsTool], instead of opening
 * the app and blindly navigating with accessibility gestures.
 */
fun launchAppTool(
    context: Context,
    invocationContext: ToolInvocationContext = ToolInvocationContext.EMPTY,
    streamer: InteractiveToolStreamer = InteractiveToolStreamer.NoOp,
): Tool = Tool(
    name = "launch_app",
    description = """
        Open an installed app by package name. Optionally provide activity_name to open one
        specific exported Android activity directly. Call list_installed_apps with package_name
        to enumerate that package's activities first. Omit activity_name for the normal launcher
        entry point. After launch, screen-automation tools can drive the foreground UI.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("package_name", buildJsonObject {
                    put("type", "string")
                    put("description", "Application package id, e.g. com.android.settings")
                })
                put("activity_name", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "Optional activity class returned by list_installed_apps(package_name=...). A leading '.' is resolved against package_name.",
                    )
                })
            },
            required = listOf("package_name"),
        )
    },
    execute = { input ->
        val packageName = input.jsonObject["package_name"]?.jsonPrimitive?.contentOrNull
        if (packageName.isNullOrBlank()) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject { put("error", "package_name is required") }.toString(),
                ),
            )
        }

        val rawActivityName = input.jsonObject["activity_name"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
        val activityName = rawActivityName?.let { resolveActivityName(packageName, it) }
        val intent = if (activityName != null) {
            Intent()
                .setClassName(packageName, activityName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            context.packageManager.getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent == null) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", "no_launch_intent")
                        put("package", packageName)
                        put(
                            "recovery",
                            "The package may not be installed or may have no launcher activity. Call list_installed_apps to verify it, or pass an exported activity_name.",
                        )
                    }.toString(),
                ),
            )
        }

        val wasOff = !ScreenWaker.isInteractive(context)
        val woke = if (wasOff) ScreenWaker.wakeIfOff(context) else false
        val keyguardLocked = ScreenWaker.isKeyguardLocked(context)
        val keyguardSecure = ScreenWaker.isKeyguardSecure(context)

        val result = try {
            context.startActivity(intent)

            val accessibilityRunning = RikkaAccessibilityService.instance != null
            val finalForeground = if (accessibilityRunning && !keyguardLocked) {
                waitForForegroundPackage(packageName)
            } else {
                null
            }
            val confirmed = finalForeground == packageName

            if (accessibilityRunning && !keyguardLocked && !confirmed) {
                listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put("error", "launch_did_not_focus")
                            put("requested", packageName)
                            activityName?.let { put("activity", it) }
                            put("current_foreground", finalForeground.orEmpty())
                            put(
                                "recovery",
                                "Android accepted the launch but did not foreground the requested package within 2.5 seconds. Read the current window without a package guard, or ask the user to switch apps.",
                            )
                            if (wasOff) put("woke_screen", woke)
                        }.toString(),
                    ),
                )
            } else {
                AgentTurnTracker.recordNavigatedAway(packageName)
                AgentTurnTracker.touchPackage(packageName)
                listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put("success", true)
                            put("package", packageName)
                            activityName?.let { put("activity", it) }
                            put("confirmed_foreground", confirmed)
                            if (!accessibilityRunning) {
                                put(
                                    "note",
                                    "AccessibilityService is not bound, so foreground state could not be verified.",
                                )
                            }
                            if (wasOff) put("woke_screen", woke)
                            if (keyguardLocked) {
                                put("keyguard_locked", true)
                                put("keyguard_secure", keyguardSecure)
                                if (keyguardSecure) {
                                    put(
                                        "warn",
                                        "The screen is awake but a PIN or biometric keyguard is still visible. The user must unlock it before the activity can be driven.",
                                    )
                                }
                            }
                        }.toString(),
                    ),
                )
            }
        } catch (error: SecurityException) {
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", "activity_not_exported")
                        put("package", packageName)
                        activityName?.let { put("activity", it) }
                        put("reason", error.message ?: "SecurityException")
                        put(
                            "recovery",
                            "Android only permits another app to launch activities declared exported=true. List the package again with exported_only=true, or open the normal app entry point and navigate from there.",
                        )
                    }.toString(),
                ),
            )
        } catch (error: Throwable) {
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", "launch_failed")
                        put("package", packageName)
                        activityName?.let { put("activity", it) }
                        put("reason", error.message ?: error::class.java.simpleName)
                        put(
                            "recovery",
                            "Verify the package and activity names with list_installed_apps before retrying.",
                        )
                    }.toString(),
                ),
            )
        }

        val streamLabel = if (activityName == null) {
            "LaunchApp $packageName"
        } else {
            "LaunchActivity $packageName/$activityName"
        }
        streamer.streamIfHeadless(invocationContext, streamLabel)
        result
    },
)

/**
 * Lists installed applications by default. When package_name is supplied, the same tool switches
 * to activity discovery and returns every declared activity with its exported flag and label.
 * This keeps the existing tool name and all persisted assistant configurations compatible while
 * adding the useful upstream activity-navigation capability.
 */
fun listInstalledAppsTool(context: Context): Tool = Tool(
    name = "list_installed_apps",
    description = """
        List installed apps as {label, package, has_launcher}. To inspect one app's Android
        activities instead, pass package_name; the response then contains {name, exported, label}
        rows that can be passed to launch_app as activity_name. Use exported_only=true to return
        only activities Android permits RikkaHub to launch.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("filter", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "Optional case-insensitive substring. For app listing it matches label/package; for activity listing it matches activity name/label.",
                    )
                })
                put("user_only", buildJsonObject {
                    put("type", "boolean")
                    put("description", "For app listing: only user-installed apps by default")
                })
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum returned rows. Default 200 for apps, 100 for activities.")
                })
                put("include_no_launcher", buildJsonObject {
                    put("type", "boolean")
                    put(
                        "description",
                        "For app listing: include service-only packages without launcher entries. Automatically enabled when filter is present.",
                    )
                })
                put("package_name", buildJsonObject {
                    put("type", "string")
                    put("description", "When set, list activities declared by this package instead of listing apps")
                })
                put("exported_only", buildJsonObject {
                    put("type", "boolean")
                    put("description", "For activity listing: return only exported, externally launchable activities")
                })
            },
        )
    },
    execute = { input ->
        val packageName = input.jsonObject["package_name"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
        val filter = input.jsonObject["filter"]?.jsonPrimitive?.contentOrNull
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }

        if (packageName != null) {
            val exportedOnly = input.jsonObject["exported_only"]?.jsonPrimitive?.contentOrNull
                ?.toBooleanStrictOrNull() ?: false
            val limit = input.jsonObject["limit"]?.jsonPrimitive?.contentOrNull
                ?.toIntOrNull()?.coerceIn(1, 500) ?: 100
            val packageManager = context.packageManager
            val activities = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()),
                    ).activities
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES).activities
                }
            } catch (_: PackageManager.NameNotFoundException) {
                return@Tool listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put("error", "package_not_found")
                            put("package", packageName)
                            put("recovery", "Call list_installed_apps without package_name to find the correct package id.")
                        }.toString(),
                    ),
                )
            }

            val all = activities.orEmpty()
            val matched = all.filter { activity ->
                if (exportedOnly && !activity.exported) return@filter false
                if (filter == null) return@filter true
                val label = runCatching { activity.loadLabel(packageManager).toString() }
                    .getOrNull().orEmpty()
                activity.name.lowercase().contains(filter) || label.lowercase().contains(filter)
            }

            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("package", packageName)
                        put("total", all.size)
                        put("matched", matched.size)
                        put("returned", minOf(matched.size, limit))
                        put("activities", buildJsonArray {
                            matched.take(limit).forEach { activity ->
                                addJsonObject {
                                    put("name", activity.name)
                                    put("exported", activity.exported)
                                    runCatching { activity.loadLabel(packageManager).toString() }
                                        .getOrNull()
                                        ?.takeIf { it.isNotBlank() && it != activity.name }
                                        ?.let { put("label", it) }
                                }
                            }
                        })
                    }.toString(),
                ),
            )
        }

        val userOnly = input.jsonObject["user_only"]?.jsonPrimitive?.contentOrNull
            ?.toBooleanStrictOrNull() ?: true
        val limit = input.jsonObject["limit"]?.jsonPrimitive?.contentOrNull
            ?.toIntOrNull()?.coerceIn(1, 1000) ?: 200
        val includeNoLauncher = (
            input.jsonObject["include_no_launcher"]?.jsonPrimitive?.contentOrNull
                ?.toBooleanStrictOrNull() ?: false
            ) || filter != null

        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val launcherResolved = packageManager.queryIntentActivities(launcherIntent, 0)
        val launcherPackages = launcherResolved.mapNotNull { it.activityInfo?.packageName }.toHashSet()

        data class AppRow(val label: String, val packageName: String, val hasLauncher: Boolean)

        val rows = mutableListOf<AppRow>()
        val seenPackages = mutableSetOf<String>()

        for (resolved in launcherResolved) {
            val installedPackage = resolved.activityInfo?.packageName ?: continue
            if (!seenPackages.add(installedPackage)) continue
            if (userOnly) {
                try {
                    val flags = packageManager.getApplicationInfo(installedPackage, 0).flags
                    val isSystem = flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
                    val isUpdatedSystemApp =
                        flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                    if (isSystem && !isUpdatedSystemApp) continue
                } catch (_: PackageManager.NameNotFoundException) {
                    continue
                }
            }
            val label = resolved.loadLabel(packageManager).toString()
            if (
                filter != null &&
                !label.lowercase().contains(filter) &&
                !installedPackage.lowercase().contains(filter)
            ) {
                continue
            }
            rows.add(AppRow(label, installedPackage, hasLauncher = true))
            if (rows.size >= limit) break
        }

        if (rows.size < limit && includeNoLauncher) {
            @Suppress("DEPRECATION")
            val packages = try {
                packageManager.getInstalledPackages(0)
            } catch (_: Throwable) {
                emptyList()
            }
            for (packageInfo in packages) {
                val installedPackage = packageInfo.packageName
                if (!seenPackages.add(installedPackage)) continue
                val applicationInfo = packageInfo.applicationInfo ?: continue
                if (userOnly) {
                    val isSystem =
                        applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
                    val isUpdatedSystemApp =
                        applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                    if (isSystem && !isUpdatedSystemApp) continue
                }
                val label = applicationInfo.loadLabel(packageManager).toString()
                if (
                    filter != null &&
                    !label.lowercase().contains(filter) &&
                    !installedPackage.lowercase().contains(filter)
                ) {
                    continue
                }
                rows.add(
                    AppRow(
                        label = label,
                        packageName = installedPackage,
                        hasLauncher = installedPackage in launcherPackages,
                    ),
                )
                if (rows.size >= limit) break
            }
        }

        rows.sortWith(compareBy({ it.label.lowercase() }, { it.packageName }))
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("count", rows.size)
                    put("apps", buildJsonArray {
                        rows.forEach { row ->
                            addJsonObject {
                                put("label", row.label)
                                put("package", row.packageName)
                                put("has_launcher", row.hasLauncher)
                            }
                        }
                    })
                }.toString(),
            ),
        )
    },
)

/**
 * Hands a URL to its system handler. This is faster and more reliable than driving a browser,
 * dialer, map, or mail client through accessibility gestures.
 */
fun openUrlTool(
    context: Context,
    invocationContext: ToolInvocationContext = ToolInvocationContext.EMPTY,
    streamer: InteractiveToolStreamer = InteractiveToolStreamer.NoOp,
): Tool = Tool(
    name = "open_url",
    description = """
        Open a URL in the system handler app: browser for http/https, dialer for tel:, maps for
        geo:, and mail client for mailto:. Optionally provide package_name to force a particular
        handler. Prefer this over accessibility navigation whenever the request maps to a URL.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("url", buildJsonObject {
                    put("type", "string")
                    put("description", "Full URL including its scheme")
                })
                put("package_name", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional package id of the handler app")
                })
            },
            required = listOf("url"),
        )
    },
    execute = { input ->
        val url = input.jsonObject["url"]?.jsonPrimitive?.contentOrNull
        if (url.isNullOrBlank()) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject { put("error", "url is required") }.toString(),
                ),
            )
        }
        val packageName = input.jsonObject["package_name"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }

        val wasOff = !ScreenWaker.isInteractive(context)
        val woke = if (wasOff) ScreenWaker.wakeIfOff(context) else false
        val keyguardLocked = ScreenWaker.isKeyguardLocked(context)
        val keyguardSecure = ScreenWaker.isKeyguardSecure(context)

        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            packageName?.let(::setPackage)
        }
        val resolved = context.packageManager.resolveActivity(intent, 0)
        if (resolved == null) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", "no_handler")
                        put("url", url)
                        packageName?.let { put("package", it) }
                        put(
                            "recovery",
                            "No installed application handles this URL scheme. Try another URL or install an appropriate handler.",
                        )
                    }.toString(),
                ),
            )
        }

        val result = try {
            context.startActivity(intent)
            val handlerPackage = resolved.activityInfo?.packageName
            AgentTurnTracker.recordNavigatedAway(handlerPackage)
            if (!handlerPackage.isNullOrBlank()) AgentTurnTracker.touchPackage(handlerPackage)
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", true)
                        put("url", url)
                        put("handler", handlerPackage.orEmpty())
                        if (wasOff) put("woke_screen", woke)
                        if (keyguardLocked) {
                            put("keyguard_locked", true)
                            put("keyguard_secure", keyguardSecure)
                            if (keyguardSecure) {
                                put(
                                    "warn",
                                    "The screen is awake but the keyguard must be unlocked before the handler is visible.",
                                )
                            }
                        }
                    }.toString(),
                ),
            )
        } catch (error: Throwable) {
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", "open_failed")
                        put("reason", error.message ?: error::class.java.simpleName)
                    }.toString(),
                ),
            )
        }
        streamer.streamIfHeadless(invocationContext, "OpenUrl ${url.take(60)}")
        result
    },
)
