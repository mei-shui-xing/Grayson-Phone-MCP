package com.danielealbano.androidremotecontrolmcp.services.safety

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local emergency stop for accessibility actions, gestures, and text input.
 *
 * Read-only MCP tools remain available while this gate is paused. The state is
 * persisted so a process restart cannot silently re-enable remote control.
 */
@Singleton
class RemoteControlGate
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val preferences =
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        private val _enabled =
            MutableStateFlow(preferences.getBoolean(KEY_ENABLED, true))

        val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

        fun isEnabled(): Boolean = _enabled.value

        fun setEnabled(enabled: Boolean) {
            preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
            _enabled.value = enabled
        }

        companion object {
            private const val PREFERENCES_NAME = "remote_control_safety"
            private const val KEY_ENABLED = "remote_control_enabled"
            const val PAUSED_MESSAGE =
                "Remote touch control is paused on the device. Resume it from the persistent notification."
        }
    }
