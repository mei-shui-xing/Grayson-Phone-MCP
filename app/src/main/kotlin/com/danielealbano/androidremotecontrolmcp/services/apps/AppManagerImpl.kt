package com.danielealbano.androidremotecontrolmcp.services.apps

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.danielealbano.androidremotecontrolmcp.data.model.AppFilter
import com.danielealbano.androidremotecontrolmcp.data.model.AppInfo
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityServiceProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Default implementation of [AppManager] backed by Android's [PackageManager]
 * and [ActivityManager].
 */
@Suppress("TooGenericExceptionCaught")
class AppManagerImpl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val accessibilityServiceProvider: AccessibilityServiceProvider,
    ) : AppManager {
        override suspend fun listInstalledApps(
            filter: AppFilter,
            nameQuery: String?,
        ): List<AppInfo> {
            val pm = context.packageManager
            val applications = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            return applications
                .asSequence()
                .filter { appInfo ->
                    when (filter) {
                        AppFilter.ALL -> true
                        AppFilter.USER -> (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                        AppFilter.SYSTEM -> (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    }
                }.map { appInfo ->
                    appInfo to pm.getApplicationLabel(appInfo).toString()
                }.filter { (_, label) ->
                    if (nameQuery != null) {
                        label.contains(nameQuery, ignoreCase = true)
                    } else {
                        true
                    }
                }.map { (appInfo, label) ->
                    val packageInfo =
                        try {
                            pm.getPackageInfo(appInfo.packageName, 0)
                        } catch (_: PackageManager.NameNotFoundException) {
                            null
                        }
                    AppInfo(
                        packageId = appInfo.packageName,
                        name = label,
                        versionName = packageInfo?.versionName,
                        versionCode = packageInfo?.let(PackageInfoCompat::getLongVersionCode) ?: 0L,
                        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        firstInstallTime = packageInfo?.firstInstallTime ?: 0L,
                        lastUpdateTime = packageInfo?.lastUpdateTime ?: 0L,
                        isLaunchable =
                            try {
                                pm.getLaunchIntentForPackage(appInfo.packageName) != null
                            } catch (e: RuntimeException) {
                                Log.w(TAG, "Unable to inspect launch intent for ${appInfo.packageName}", e)
                                false
                            },
                    )
                }.sortedBy { it.name.lowercase() }
                .toList()
        }

        override suspend fun openApp(packageId: String): Result<Unit> =
            try {
                val intent =
                    context.packageManager.getLaunchIntentForPackage(packageId)
                        ?: return Result.failure(
                            IllegalArgumentException("No launchable activity found for package '$packageId'"),
                        )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val launchContext = accessibilityServiceProvider.getContext() ?: context
                sendLaunchPendingIntent(launchContext, packageId, intent)
                Log.i(TAG, "Launched application: $packageId via trusted PendingIntent")
                Result.success(Unit)
            } catch (e: PendingIntent.CanceledException) {
                Log.e(TAG, "PendingIntent cancelled for package: $packageId", e)
                Result.failure(e)
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "Activity not found for package: $packageId", e)
                Result.failure(e)
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception launching package: $packageId", e)
                Result.failure(e)
            }

        @Suppress("DEPRECATION")
        private fun sendLaunchPendingIntent(
            launchContext: Context,
            packageId: String,
            intent: Intent,
        ) {
            val options = ActivityOptions.makeBasic()
            when {
                Build.VERSION.SDK_INT >= 36 -> {
                    options.setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS,
                    )
                    options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS,
                    )
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                    options.setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                    options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                }

                else -> {
                    options.setPendingIntentBackgroundActivityLaunchAllowed(true)
                }
            }

            val optionBundle = options.toBundle()
            val pendingIntent =
                PendingIntent.getActivity(
                    launchContext,
                    packageId.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    optionBundle,
                )
            pendingIntent.send(launchContext, 0, null, null, null, null, optionBundle)
        }

        override suspend fun closeApp(packageId: String): Result<Unit> {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.killBackgroundProcesses(packageId)
            Log.i(TAG, "Requested kill of background processes for: $packageId")
            return Result.success(Unit)
        }

        companion object {
            private const val TAG = "MCP:AppManager"
        }
    }
