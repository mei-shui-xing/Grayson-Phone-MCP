package com.danielealbano.androidremotecontrolmcp.services.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.UserManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class AppUsageEntry(
    val packageId: String,
    val name: String,
    val foregroundTimeMs: Long,
    val launchCount: Int,
    val lastTimeUsedMs: Long,
)

data class ScreenTimeReport(
    val interactiveTimeMs: Long,
    val unlockCount: Int,
)

/** Reads Android's user-approved Usage Access history without privileged APIs. */
@Singleton
class UsageStatsProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)

        fun hasPermission(): Boolean {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            return appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        }

        fun requireAvailable() {
            check(hasPermission()) {
                "Usage Access is not granted. On the phone open Settings > Special app access > " +
                    "Usage access, then enable Android Remote Control MCP."
            }
            val userManager = context.getSystemService(UserManager::class.java)
            check(userManager.isUserUnlocked) {
                "Usage history is unavailable while the phone is locked. Unlock the phone and retry."
            }
        }

        @Suppress("CyclomaticComplexMethod")
        fun queryAppUsage(
            startTimeMs: Long,
            endTimeMs: Long,
            packageId: String? = null,
        ): List<AppUsageEntry> {
            requireAvailable()
            validateRange(startTimeMs, endTimeMs)

            val activeSince = mutableMapOf<String, Long>()
            val foregroundMs = mutableMapOf<String, Long>()
            val launchCount = mutableMapOf<String, Int>()
            val lastUsed = mutableMapOf<String, Long>()

            // Infer which package was already foreground at the requested boundary.
            consumeAppEvents(
                usageStatsManager.queryEvents(
                    (startTimeMs - STATE_LOOKBACK_MS).coerceAtLeast(0L),
                    startTimeMs,
                ),
            ) { event ->
                if (packageId == null || event.packageName == packageId) {
                    when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> activeSince[event.packageName] = startTimeMs
                        UsageEvents.Event.ACTIVITY_PAUSED -> activeSince.remove(event.packageName)
                    }
                }
            }

            consumeAppEvents(usageStatsManager.queryEvents(startTimeMs, endTimeMs)) { event ->
                val pkg = event.packageName ?: return@consumeAppEvents
                if (packageId != null && pkg != packageId) return@consumeAppEvents
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        if (activeSince.putIfAbsent(pkg, event.timeStamp) == null) {
                            launchCount[pkg] = (launchCount[pkg] ?: 0) + 1
                        }
                        lastUsed[pkg] = maxOf(lastUsed[pkg] ?: 0L, event.timeStamp)
                    }

                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        val started = activeSince.remove(pkg)
                        if (started != null) {
                            foregroundMs[pkg] =
                                (foregroundMs[pkg] ?: 0L) +
                                (event.timeStamp.coerceAtMost(endTimeMs) - started).coerceAtLeast(0L)
                        }
                    }
                }
            }

            activeSince.forEach { (pkg, started) ->
                foregroundMs[pkg] = (foregroundMs[pkg] ?: 0L) + (endTimeMs - started).coerceAtLeast(0L)
            }

            val packages = foregroundMs.keys + launchCount.keys + lastUsed.keys
            return packages
                .map { pkg ->
                    AppUsageEntry(
                        packageId = pkg,
                        name = applicationLabel(pkg),
                        foregroundTimeMs = foregroundMs[pkg] ?: 0L,
                        launchCount = launchCount[pkg] ?: 0,
                        lastTimeUsedMs = lastUsed[pkg] ?: 0L,
                    )
                }.sortedByDescending { it.foregroundTimeMs }
        }

        fun queryScreenTime(
            startTimeMs: Long,
            endTimeMs: Long,
        ): ScreenTimeReport {
            requireAvailable()
            validateRange(startTimeMs, endTimeMs)

            var interactive = false
            consumeAllEvents(
                usageStatsManager.queryEvents(
                    (startTimeMs - STATE_LOOKBACK_MS).coerceAtLeast(0L),
                    startTimeMs,
                ),
            ) { event ->
                when (event.eventType) {
                    UsageEvents.Event.SCREEN_INTERACTIVE -> interactive = true
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> interactive = false
                }
            }

            var interactiveSince = if (interactive) startTimeMs else null
            var totalMs = 0L
            var unlockCount = 0
            consumeAllEvents(usageStatsManager.queryEvents(startTimeMs, endTimeMs)) { event ->
                when (event.eventType) {
                    UsageEvents.Event.SCREEN_INTERACTIVE -> {
                        if (interactiveSince == null) interactiveSince = event.timeStamp
                    }

                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        val started = interactiveSince
                        if (started != null) {
                            totalMs += (event.timeStamp.coerceAtMost(endTimeMs) - started).coerceAtLeast(0L)
                            interactiveSince = null
                        }
                    }

                    UsageEvents.Event.KEYGUARD_HIDDEN -> {
                        unlockCount++
                    }
                }
            }
            interactiveSince?.let { totalMs += (endTimeMs - it).coerceAtLeast(0L) }
            return ScreenTimeReport(totalMs, unlockCount)
        }

        private fun applicationLabel(packageId: String): String =
            try {
                val info = context.packageManager.getApplicationInfo(packageId, 0)
                context.packageManager.getApplicationLabel(info).toString()
            } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                packageId
            }

        private fun consumeAppEvents(
            events: UsageEvents?,
            consumer: (UsageEvents.Event) -> Unit,
        ) = consumeAllEvents(events, consumer)

        private fun consumeAllEvents(
            events: UsageEvents?,
            consumer: (UsageEvents.Event) -> Unit,
        ) {
            if (events == null) return
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                consumer(event)
            }
        }

        private fun validateRange(
            startTimeMs: Long,
            endTimeMs: Long,
        ) {
            require(startTimeMs >= 0L && endTimeMs > startTimeMs) {
                "end_time_ms must be greater than start_time_ms"
            }
            require(endTimeMs - startTimeMs <= MAX_RANGE_MS) {
                "Usage queries are limited to 31 days"
            }
        }

        companion object {
            private const val STATE_LOOKBACK_MS = 24L * 60L * 60L * 1000L
            private const val MAX_RANGE_MS = 31L * 24L * 60L * 60L * 1000L
        }
    }
