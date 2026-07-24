package com.danielealbano.androidremotecontrolmcp.mcp.tools

import android.provider.AlarmClock
import com.danielealbano.androidremotecontrolmcp.data.model.ToolPermissionsConfig
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.services.intents.IntentDispatcher
import com.danielealbano.androidremotecontrolmcp.services.intents.SendIntentRequest
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class OpenAppByNameHandler(
    private val reliableAppLauncher: ReliableAppLauncher,
) {
    suspend fun execute(arguments: kotlinx.serialization.json.JsonObject?): CallToolResult {
        val query = McpToolUtils.requireString(arguments, "app_name")
        val app = reliableAppLauncher.openByName(query)
        return McpToolUtils.textResult(
            "Application '${app.name}' opened successfully (${app.packageId}).",
        )
    }

    fun register(
        server: Server,
        toolNamePrefix: String,
    ) {
        server.addTool(
            name = "$toolNamePrefix$TOOL_NAME",
            description =
                "Reliably opens an installed application by its human-readable name. " +
                    "Uses a direct launch first, then falls back to the device's desktop app search " +
                    "when the system blocks background activity launches.",
            inputSchema =
                ToolSchema(
                    properties =
                        buildJsonObject {
                            putJsonObject("app_name") {
                                put("type", "string")
                                put("description", "Displayed app name, such as '微信' or '美团'")
                            }
                        },
                    required = listOf("app_name"),
                ),
        ) { request -> execute(request.arguments) }
    }

    companion object {
        const val TOOL_NAME = "open_app_by_name"
    }
}

class SetAlarmHandler(
    private val intentDispatcher: IntentDispatcher,
) {
    suspend fun execute(arguments: kotlinx.serialization.json.JsonObject?): CallToolResult {
        val hour = McpToolUtils.requireInt(arguments, "hour")
        val minute = McpToolUtils.requireInt(arguments, "minute")
        if (hour !in 0..23) {
            throw McpToolException.InvalidParams("Parameter 'hour' must be between 0 and 23")
        }
        if (minute !in 0..59) {
            throw McpToolException.InvalidParams("Parameter 'minute' must be between 0 and 59")
        }

        val label = McpToolUtils.optionalString(arguments, "label", "").trim()
        val skipUi = McpToolUtils.optionalBoolean(arguments, "skip_ui", true)
        val extras =
            mutableMapOf<String, Any?>(
                AlarmClock.EXTRA_HOUR to hour,
                AlarmClock.EXTRA_MINUTES to minute,
                AlarmClock.EXTRA_SKIP_UI to skipUi,
            )
        if (label.isNotEmpty()) extras[AlarmClock.EXTRA_MESSAGE] = label

        val result =
            intentDispatcher.sendIntent(
                SendIntentRequest(
                    type = "activity",
                    action = AlarmClock.ACTION_SET_ALARM,
                    extras = extras,
                ),
            )
        val formattedTime = "%02d:%02d".format(hour, minute)
        return McpToolUtils.handleActionResult(
            result,
            "Alarm set for $formattedTime${label.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()}.",
        )
    }

    fun register(
        server: Server,
        toolNamePrefix: String,
    ) {
        server.addTool(
            name = "$toolNamePrefix$TOOL_NAME",
            description =
                "Sets an alarm in the device's default clock app. " +
                    "By default the alarm is created directly without opening the clock UI.",
            inputSchema = alarmInputSchema(),
        ) { request -> execute(request.arguments) }
    }

    companion object {
        const val TOOL_NAME = "set_alarm"
    }
}

class SetTimerHandler(
    private val intentDispatcher: IntentDispatcher,
) {
    suspend fun execute(arguments: kotlinx.serialization.json.JsonObject?): CallToolResult {
        val seconds = McpToolUtils.requireInt(arguments, "seconds")
        if (seconds !in 1..MAX_TIMER_SECONDS) {
            throw McpToolException.InvalidParams(
                "Parameter 'seconds' must be between 1 and $MAX_TIMER_SECONDS",
            )
        }
        val label = McpToolUtils.optionalString(arguments, "label", "").trim()
        val skipUi = McpToolUtils.optionalBoolean(arguments, "skip_ui", true)
        val extras =
            mutableMapOf<String, Any?>(
                AlarmClock.EXTRA_LENGTH to seconds,
                AlarmClock.EXTRA_SKIP_UI to skipUi,
            )
        if (label.isNotEmpty()) extras[AlarmClock.EXTRA_MESSAGE] = label

        val result =
            intentDispatcher.sendIntent(
                SendIntentRequest(
                    type = "activity",
                    action = AlarmClock.ACTION_SET_TIMER,
                    extras = extras,
                ),
            )
        val minutes = seconds / 60
        val remainder = seconds % 60
        val duration =
            if (remainder == 0) {
                "$minutes minute(s)"
            } else {
                "$minutes minute(s) $remainder second(s)"
            }
        return McpToolUtils.handleActionResult(
            result,
            "Timer set for $duration${label.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()}.",
        )
    }

    fun register(
        server: Server,
        toolNamePrefix: String,
    ) {
        server.addTool(
            name = "$toolNamePrefix$TOOL_NAME",
            description =
                "Starts a countdown timer in the device's default clock app. " +
                    "By default the timer is created directly without opening the clock UI.",
            inputSchema = timerInputSchema(),
        ) { request -> execute(request.arguments) }
    }

    companion object {
        const val TOOL_NAME = "set_timer"
        private const val MAX_TIMER_SECONDS = 604_800
    }
}

class ShowAlarmsHandler(
    private val intentDispatcher: IntentDispatcher,
) {
    suspend fun execute(): CallToolResult {
        val result =
            intentDispatcher.sendIntent(
                SendIntentRequest(
                    type = "activity",
                    action = AlarmClock.ACTION_SHOW_ALARMS,
                ),
            )
        return McpToolUtils.handleActionResult(result, "Clock alarm list opened.")
    }

    fun register(
        server: Server,
        toolNamePrefix: String,
    ) {
        server.addTool(
            name = "$toolNamePrefix$TOOL_NAME",
            description = "Opens the alarm list in the device's default clock app.",
            inputSchema = ToolSchema(properties = buildJsonObject {}, required = emptyList()),
        ) { execute() }
    }

    companion object {
        const val TOOL_NAME = "show_alarms"
    }
}

private fun alarmInputSchema(): ToolSchema =
    ToolSchema(
        properties =
            buildJsonObject {
                putJsonObject("hour") {
                    put("type", "integer")
                    put("minimum", 0)
                    put("maximum", 23)
                    put("description", "Alarm hour in 24-hour time (0-23)")
                }
                putJsonObject("minute") {
                    put("type", "integer")
                    put("minimum", 0)
                    put("maximum", 59)
                    put("description", "Alarm minute (0-59)")
                }
                putJsonObject("label") {
                    put("type", "string")
                    put("description", "Optional alarm label")
                }
                putJsonObject("skip_ui") {
                    put("type", "boolean")
                    put("description", "Create directly without showing the clock UI")
                    put("default", true)
                }
            },
        required = listOf("hour", "minute"),
    )

private fun timerInputSchema(): ToolSchema =
    ToolSchema(
        properties =
            buildJsonObject {
                putJsonObject("seconds") {
                    put("type", "integer")
                    put("minimum", 1)
                    put("maximum", 604_800)
                    put("description", "Countdown length in seconds (maximum 7 days)")
                }
                putJsonObject("label") {
                    put("type", "string")
                    put("description", "Optional timer label")
                }
                putJsonObject("skip_ui") {
                    put("type", "boolean")
                    put("description", "Start directly without showing the clock UI")
                    put("default", true)
                }
            },
        required = listOf("seconds"),
    )

fun registerConvenienceTools(
    server: Server,
    reliableAppLauncher: ReliableAppLauncher,
    intentDispatcher: IntentDispatcher,
    toolNamePrefix: String,
    perms: ToolPermissionsConfig,
) {
    if (perms.isToolEnabled(OpenAppByNameHandler.TOOL_NAME)) {
        OpenAppByNameHandler(reliableAppLauncher).register(server, toolNamePrefix)
    }
    if (perms.isToolEnabled(SetAlarmHandler.TOOL_NAME)) {
        SetAlarmHandler(intentDispatcher).register(server, toolNamePrefix)
    }
    if (perms.isToolEnabled(SetTimerHandler.TOOL_NAME)) {
        SetTimerHandler(intentDispatcher).register(server, toolNamePrefix)
    }
    if (perms.isToolEnabled(ShowAlarmsHandler.TOOL_NAME)) {
        ShowAlarmsHandler(intentDispatcher).register(server, toolNamePrefix)
    }
}
