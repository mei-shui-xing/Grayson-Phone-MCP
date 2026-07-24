package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.data.model.ToolPermissionsConfig
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.services.usage.AppUsageEntry
import com.danielealbano.androidremotecontrolmcp.services.usage.UsageStatsProvider
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.Calendar

private data class TimeRange(
    val startMs: Long,
    val endMs: Long,
    val period: String,
)

/** Registers user-approved app usage and screen-time queries. */
class UsageStatsTools(
    private val provider: UsageStatsProvider,
) {
    fun register(
        server: Server,
        toolNamePrefix: String,
        perms: ToolPermissionsConfig,
    ) {
        if (perms.isToolEnabled(USAGE_SUMMARY)) registerSummary(server, toolNamePrefix)
        if (perms.isToolEnabled(APP_USAGE)) registerAppUsage(server, toolNamePrefix)
        if (perms.isToolEnabled(SCREEN_TIME)) registerScreenTime(server, toolNamePrefix)
    }

    private fun registerSummary(
        server: Server,
        prefix: String,
    ) {
        server.addTool(
            name = "$prefix$USAGE_SUMMARY",
            description =
                "Returns screen time, unlock count, and the most-used apps for a time range. " +
                    "Requires Usage Access granted manually on the phone.",
            inputSchema = rangeSchema(includePackage = false, includeLimit = true),
        ) { request ->
            executeSafely {
                val range = resolveRange(request.arguments)
                val limit = McpToolUtils.optionalInt(request.arguments, "limit", DEFAULT_LIMIT)
                if (limit !in 1..MAX_LIMIT) {
                    throw McpToolException.InvalidParams("Parameter 'limit' must be between 1 and $MAX_LIMIT")
                }
                val screen = provider.queryScreenTime(range.startMs, range.endMs)
                val apps = provider.queryAppUsage(range.startMs, range.endMs).take(limit)
                McpToolUtils.untrustedTextResult(
                    Json.encodeToString(
                        buildJsonObject {
                            putRange(range)
                            put("screen_time_ms", screen.interactiveTimeMs)
                            put("unlock_count", screen.unlockCount)
                            put("calculation_method", "Android UsageEvents; values are approximate")
                            put("apps", appArray(apps))
                        },
                    ),
                )
            }
        }
    }

    private fun registerAppUsage(
        server: Server,
        prefix: String,
    ) {
        server.addTool(
            name = "$prefix$APP_USAGE",
            description =
                "Returns foreground time, launch count, and last-use time for one installed package. " +
                    "Requires Usage Access granted manually on the phone.",
            inputSchema = rangeSchema(includePackage = true, includeLimit = false),
        ) { request ->
            executeSafely {
                val packageId = McpToolUtils.requireString(request.arguments, "package_id")
                val range = resolveRange(request.arguments)
                val entry = provider.queryAppUsage(range.startMs, range.endMs, packageId).firstOrNull()
                McpToolUtils.untrustedTextResult(
                    Json.encodeToString(
                        buildJsonObject {
                            putRange(range)
                            put("package_id", packageId)
                            put("found_in_range", entry != null)
                            entry?.let { putApp(it) }
                            put("calculation_method", "Android UsageEvents; values are approximate")
                        },
                    ),
                )
            }
        }
    }

    private fun registerScreenTime(
        server: Server,
        prefix: String,
    ) {
        server.addTool(
            name = "$prefix$SCREEN_TIME",
            description =
                "Returns approximate interactive screen time and unlock count. " +
                    "Requires Usage Access granted manually on the phone.",
            inputSchema = rangeSchema(includePackage = false, includeLimit = false),
        ) { request ->
            executeSafely {
                val range = resolveRange(request.arguments)
                val report = provider.queryScreenTime(range.startMs, range.endMs)
                McpToolUtils.untrustedTextResult(
                    Json.encodeToString(
                        buildJsonObject {
                            putRange(range)
                            put("screen_time_ms", report.interactiveTimeMs)
                            put("unlock_count", report.unlockCount)
                            put("calculation_method", "Android UsageEvents; values are approximate")
                        },
                    ),
                )
            }
        }
    }

    @Suppress("SwallowedException")
    private suspend fun executeSafely(block: suspend () -> CallToolResult): CallToolResult =
        try {
            block()
        } catch (e: McpToolException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw McpToolException.InvalidParams(e.message ?: "Invalid usage query")
        } catch (e: IllegalStateException) {
            throw McpToolException.ActionFailed(e.message ?: "Usage history is unavailable")
        }

    private fun resolveRange(arguments: JsonObject?): TimeRange {
        val period = McpToolUtils.optionalString(arguments, "period", "today").lowercase()
        val now = System.currentTimeMillis()
        val today =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        return when (period) {
            "today" -> {
                TimeRange(today.timeInMillis, now, period)
            }

            "yesterday" -> {
                val end = today.timeInMillis
                today.add(Calendar.DAY_OF_YEAR, -1)
                TimeRange(today.timeInMillis, end, period)
            }

            "last_7_days" -> {
                today.add(Calendar.DAY_OF_YEAR, LAST_SEVEN_DAYS_OFFSET)
                TimeRange(today.timeInMillis, now, period)
            }

            "custom" -> {
                val start = McpToolUtils.requireLong(arguments, "start_time_ms")
                val end = McpToolUtils.requireLong(arguments, "end_time_ms")
                TimeRange(start, end, period)
            }

            else -> {
                throw McpToolException.InvalidParams(
                    "Parameter 'period' must be one of: today, yesterday, last_7_days, custom",
                )
            }
        }
    }

    private fun rangeSchema(
        includePackage: Boolean,
        includeLimit: Boolean,
    ): ToolSchema =
        ToolSchema(
            properties =
                buildJsonObject {
                    putJsonObject("period") {
                        put("type", "string")
                        put(
                            "enum",
                            buildJsonArray {
                                listOf("today", "yesterday", "last_7_days", "custom")
                                    .forEach { add(JsonPrimitive(it)) }
                            },
                        )
                        put("default", "today")
                    }
                    putJsonObject("start_time_ms") {
                        put("type", "integer")
                        put("description", "Required only when period is custom; Unix epoch milliseconds")
                    }
                    putJsonObject("end_time_ms") {
                        put("type", "integer")
                        put("description", "Required only when period is custom; Unix epoch milliseconds")
                    }
                    if (includePackage) {
                        putJsonObject("package_id") { put("type", "string") }
                    }
                    if (includeLimit) {
                        putJsonObject("limit") {
                            put("type", "integer")
                            put("minimum", 1)
                            put("maximum", MAX_LIMIT)
                            put("default", DEFAULT_LIMIT)
                        }
                    }
                },
            required = if (includePackage) listOf("package_id") else emptyList(),
        )

    private fun appArray(apps: List<AppUsageEntry>) =
        buildJsonArray {
            apps.forEach { app -> add(buildJsonObject { putApp(app) }) }
        }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putApp(app: AppUsageEntry) {
        put("package_id", app.packageId)
        put("name", app.name)
        put("foreground_time_ms", app.foregroundTimeMs)
        put("launch_count", app.launchCount)
        put("last_time_used_ms", app.lastTimeUsedMs)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putRange(range: TimeRange) {
        put("period", range.period)
        put("start_time_ms", range.startMs)
        put("end_time_ms", range.endMs)
    }

    companion object {
        const val USAGE_SUMMARY = "get_usage_summary"
        const val APP_USAGE = "get_app_usage"
        const val SCREEN_TIME = "get_screen_time"
        private const val DEFAULT_LIMIT = 10
        private const val MAX_LIMIT = 50
        private const val LAST_SEVEN_DAYS_OFFSET = -6
    }
}

fun registerUsageStatsTools(
    server: Server,
    provider: UsageStatsProvider,
    toolNamePrefix: String,
    perms: ToolPermissionsConfig,
) = UsageStatsTools(provider).register(server, toolNamePrefix, perms)
