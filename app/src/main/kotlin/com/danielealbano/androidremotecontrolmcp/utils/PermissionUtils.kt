package com.danielealbano.androidremotecontrolmcp.utils

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Utility functions for checking and requesting Android permissions.
 */
object PermissionUtils {
    private const val ENABLED_SERVICES_SEPARATOR = ':'
    const val ORIGIN_OS_GET_INSTALLED_APPS_PERMISSION = "com.android.permission.GET_INSTALLED_APPS"

    /**
     * Checks whether a specific accessibility service is currently enabled.
     *
     * Reads the `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` system setting
     * and checks if the given service class is listed.
     *
     * @param context Application context.
     * @param serviceClass The accessibility service class to check (e.g., `McpAccessibilityService::class.java`).
     * @return `true` if the service is enabled, `false` otherwise.
     */
    fun isAccessibilityServiceEnabled(
        context: Context,
        serviceClass: Class<*>,
    ): Boolean {
        val expectedComponentName =
            "${context.packageName}/${serviceClass.canonicalName}"

        val enabledServices =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false

        return enabledServices
            .split(ENABLED_SERVICES_SEPARATOR)
            .any { it.equals(expectedComponentName, ignoreCase = true) }
    }

    /**
     * Opens the Android Accessibility Settings screen.
     *
     * @param context Application context. Uses [Intent.FLAG_ACTIVITY_NEW_TASK]
     *   so this can be called from non-Activity contexts.
     */
    fun openAccessibilitySettings(context: Context) {
        val intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }

    /**
     * Checks whether the `POST_NOTIFICATIONS` runtime permission is granted.
     *
     * @param context Application context.
     * @return `true` if notification permission is granted, `false` otherwise.
     */
    fun isNotificationPermissionGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    /** Returns whether this ROM exposes OriginOS's additional installed-app-list permission. */
    fun isInstalledAppsPermissionSupported(context: Context): Boolean =
        runCatching {
            context.packageManager.getPermissionInfo(ORIGIN_OS_GET_INSTALLED_APPS_PERMISSION, 0)
        }.isSuccess

    /** Checks OriginOS's runtime installed-app-list permission when that permission exists. */
    fun isInstalledAppsPermissionGranted(context: Context): Boolean =
        !isInstalledAppsPermissionSupported(context) ||
            ContextCompat.checkSelfPermission(
                context,
                ORIGIN_OS_GET_INSTALLED_APPS_PERMISSION,
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * Checks whether the `CAMERA` runtime permission is granted.
     *
     * @param context Application context.
     * @return `true` if camera permission is granted, `false` otherwise.
     */
    fun isCameraPermissionGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Checks whether the `RECORD_AUDIO` runtime permission is granted.
     *
     * @param context Application context.
     * @return `true` if microphone permission is granted, `false` otherwise.
     */
    fun isMicrophonePermissionGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Checks whether the `ACCESS_FINE_LOCATION` runtime permission is granted.
     *
     * @param context Application context.
     * @return `true` if location permission is granted, `false` otherwise.
     */
    fun isLocationPermissionGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Checks whether a specific notification listener service is currently enabled.
     *
     * Reads the `Settings.Secure` `enabled_notification_listeners` system setting
     * and checks if the given service class is listed.
     *
     * @param context Application context.
     * @param serviceClass The notification listener service class to check.
     * @return `true` if the service is enabled, `false` otherwise.
     */
    fun isNotificationListenerEnabled(
        context: Context,
        serviceClass: Class<*>,
    ): Boolean {
        val expectedComponentName =
            "${context.packageName}/${serviceClass.canonicalName}"

        val enabledListeners =
            Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ) ?: return false

        return enabledListeners
            .split(ENABLED_SERVICES_SEPARATOR)
            .any { it.equals(expectedComponentName, ignoreCase = true) }
    }

    /**
     * Opens the Android Notification Listener Settings screen.
     *
     * @param context Application context. Uses [Intent.FLAG_ACTIVITY_NEW_TASK]
     *   so this can be called from non-Activity contexts.
     */
    fun openNotificationListenerSettings(context: Context) {
        val intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }

    fun isUsageAccessGranted(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        val intent =
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }
}
