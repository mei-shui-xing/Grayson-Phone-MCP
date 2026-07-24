package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.data.model.AppFilter
import com.danielealbano.androidremotecontrolmcp.data.model.AppInfo
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityNodeCache
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityServiceProvider
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityTreeParser
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ActionExecutor
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ElementFinder
import com.danielealbano.androidremotecontrolmcp.services.accessibility.FindBy
import com.danielealbano.androidremotecontrolmcp.services.accessibility.WindowData
import com.danielealbano.androidremotecontrolmcp.services.apps.AppManager
import kotlinx.coroutines.delay

class ReliableAppLauncher(
    private val appManager: AppManager,
    private val actionExecutor: ActionExecutor,
    private val accessibilityServiceProvider: AccessibilityServiceProvider,
    private val treeParser: AccessibilityTreeParser,
    private val elementFinder: ElementFinder,
    private val nodeCache: AccessibilityNodeCache,
) {
    suspend fun openByPackage(packageId: String): AppInfo {
        val app =
            installedApps().firstOrNull { it.packageId == packageId }
                ?: throw McpToolException.ActionFailed(
                    "Application '$packageId' is not installed or not visible",
                )
        return openResolvedApp(app)
    }

    suspend fun openByName(query: String): AppInfo {
        val normalized = query.trim()
        if (normalized.isEmpty()) {
            throw McpToolException.InvalidParams("Parameter 'app_name' must not be empty")
        }
        val launchableApps = installedApps().filter(AppInfo::isLaunchable)
        val matchTiers =
            listOf(
                launchableApps.filter { it.name.equals(normalized, ignoreCase = true) },
                launchableApps.filter { it.name.startsWith(normalized, ignoreCase = true) },
                launchableApps.filter { it.name.contains(normalized, ignoreCase = true) },
            )
        val matches = matchTiers.firstOrNull { it.isNotEmpty() }.orEmpty()
        if (matches.isEmpty()) {
            throw McpToolException.ActionFailed("No launchable application matched '$normalized'")
        }
        if (matches.size > 1) {
            val choices = matches.take(MAX_AMBIGUOUS_RESULTS).joinToString { "${it.name} (${it.packageId})" }
            throw McpToolException.ActionFailed(
                "Application name '$normalized' is ambiguous. Matches: $choices",
            )
        }
        return openResolvedApp(matches.single())
    }

    private suspend fun installedApps(): List<AppInfo> = appManager.listInstalledApps(AppFilter.ALL, null)

    private suspend fun openResolvedApp(app: AppInfo): AppInfo {
        if (isTargetForeground(app.packageId)) return app

        val directResult = appManager.openApp(app.packageId)
        if (directResult.isSuccess && waitForTarget(app)) return app

        if (launchViaDesktopSearch(app) && waitForTarget(app)) return app

        val directFailure = directResult.exceptionOrNull()?.message
        val suffix = directFailure?.let { " Direct launch error: $it" }.orEmpty()
        throw McpToolException.ActionFailed(
            "Failed to bring '${app.name}' (${app.packageId}) to the foreground.$suffix",
        )
    }

    private suspend fun waitForTarget(app: AppInfo): Boolean {
        repeat(FOREGROUND_CHECK_ATTEMPTS) {
            if (isTargetForeground(app.packageId)) return true
            if (app.packageId == WECHAT_PACKAGE && currentPackage() == VIVO_CLONE_PICKER_PACKAGE) {
                choosePrimaryWechat()
            }
            chooseDefaultAppIfNeeded(app.name)
            delay(FOREGROUND_CHECK_DELAY_MS)
        }
        return isTargetForeground(app.packageId)
    }

    private fun currentPackage(): String? = accessibilityServiceProvider.getCurrentPackageName()

    private fun isTargetForeground(packageId: String): Boolean = currentPackage() == packageId

    private suspend fun launchViaDesktopSearch(app: AppInfo): Boolean {
        actionExecutor.pressHome().getOrElse { return false }
        delay(HOME_SETTLE_DELAY_MS)

        val searchWindows = navigateToDesktopSearch() ?: return false
        val searchIcon =
            elementFinder
                .findElements(
                    searchWindows,
                    FindBy.RESOURCE_ID,
                    VIVO_DESKTOP_SEARCH_ICON_ID,
                    exactMatch = true,
                ).firstOrNull(::isOnScreen)
                ?: return false
        actionExecutor.clickNode(searchIcon.id, searchWindows).getOrElse { return false }
        delay(SEARCH_OPEN_DELAY_MS)

        val browserWindows = freshWindows()
        val searchField =
            elementFinder
                .findElements(
                    browserWindows,
                    FindBy.RESOURCE_ID,
                    VIVO_SEARCH_FIELD_ID,
                    exactMatch = true,
                ).firstOrNull(::isOnScreen)
                ?: return false
        actionExecutor.setTextOnNode(searchField.id, app.name, browserWindows).getOrElse { return false }
        delay(SEARCH_RESULTS_DELAY_MS)

        val resultWindows = freshWindows()
        val localAppResult = findLocalAppResult(resultWindows, app.name) ?: return false
        return clickElement(localAppResult, resultWindows)
    }

    private suspend fun navigateToDesktopSearch(): List<WindowData>? {
        repeat(MAX_DESKTOP_NAVIGATION_ATTEMPTS) {
            val windows = freshWindows()
            val searchIcon =
                elementFinder
                    .findElements(
                        windows,
                        FindBy.RESOURCE_ID,
                        VIVO_DESKTOP_SEARCH_ICON_ID,
                        exactMatch = true,
                    ).firstOrNull()
            if (searchIcon != null && isOnScreen(searchIcon)) return windows

            val screen = accessibilityServiceProvider.getScreenInfo()
            val centerX = searchIcon?.let { (it.bounds.left + it.bounds.right) / 2 }
            val swipeResult =
                if (centerX != null && centerX < 0) {
                    actionExecutor.swipe(
                        screen.width * SWIPE_START_LOW,
                        screen.height * SWIPE_Y,
                        screen.width * SWIPE_START_HIGH,
                        screen.height * SWIPE_Y,
                        DESKTOP_SWIPE_DURATION_MS,
                    )
                } else {
                    actionExecutor.swipe(
                        screen.width * SWIPE_START_HIGH,
                        screen.height * SWIPE_Y,
                        screen.width * SWIPE_START_LOW,
                        screen.height * SWIPE_Y,
                        DESKTOP_SWIPE_DURATION_MS,
                    )
                }
            if (swipeResult.isFailure) return null
            delay(DESKTOP_SWIPE_SETTLE_MS)
        }
        return null
    }

    private fun findLocalAppResult(
        windows: List<WindowData>,
        appName: String,
    ) = elementFinder
        .findElements(
            windows,
            FindBy.CONTENT_DESC,
            VIVO_OPEN_BUTTON_DESCRIPTION,
            exactMatch = true,
        ).firstOrNull(::isOnScreen)
        ?: elementFinder
            .findElements(
                windows,
                FindBy.CONTENT_DESC,
                "$appName 本地应用",
                exactMatch = true,
            ).firstOrNull(::isOnScreen)
        ?: elementFinder
            .findElements(
                windows,
                FindBy.CONTENT_DESC,
                "$appName 本地应用",
                exactMatch = false,
            ).firstOrNull(::isOnScreen)

    private suspend fun chooseDefaultAppIfNeeded(appName: String) {
        val windows = freshWindows()
        if (findAlwaysButton(windows) == null || findOnceButton(windows) == null) return

        val appChoice =
            elementFinder
                .findElements(windows, FindBy.TEXT, appName, exactMatch = true)
                .firstOrNull(::isOnScreen)
                ?: elementFinder
                    .findElements(windows, FindBy.CONTENT_DESC, appName, exactMatch = false)
                    .firstOrNull(::isOnScreen)
        if (appChoice != null) {
            clickElement(appChoice, windows)
            delay(DEFAULT_APP_SELECTION_DELAY_MS)
        }

        val confirmWindows = freshWindows()
        val alwaysButton = findAlwaysButton(confirmWindows) ?: return
        clickElement(alwaysButton, confirmWindows)
        delay(DEFAULT_APP_SELECTION_DELAY_MS)
    }

    private fun findAlwaysButton(windows: List<WindowData>) =
        DEFAULT_APP_ALWAYS_LABELS.firstNotNullOfOrNull { label ->
            elementFinder
                .findElements(windows, FindBy.TEXT, label, exactMatch = false)
                .firstOrNull(::isOnScreen)
        }

    private fun findOnceButton(windows: List<WindowData>) =
        DEFAULT_APP_ONCE_LABELS.firstNotNullOfOrNull { label ->
            elementFinder
                .findElements(windows, FindBy.TEXT, label, exactMatch = false)
                .firstOrNull(::isOnScreen)
        }

    private suspend fun clickElement(
        element: com.danielealbano.androidremotecontrolmcp.services.accessibility.ElementInfo,
        windows: List<WindowData>,
    ): Boolean {
        if (actionExecutor.clickNode(element.id, windows).isSuccess) return true
        val x = (element.bounds.left + element.bounds.right) / 2f
        val y = (element.bounds.top + element.bounds.bottom) / 2f
        return actionExecutor.tap(x, y).isSuccess
    }

    private suspend fun choosePrimaryWechat() {
        val windows = freshWindows()
        val mainWechat =
            elementFinder
                .findElements(
                    windows,
                    FindBy.RESOURCE_ID,
                    VIVO_PRIMARY_WECHAT_ID,
                    exactMatch = true,
                ).firstOrNull(::isOnScreen)
                ?: return
        clickElement(mainWechat, windows)
        delay(WECHAT_PICKER_DELAY_MS)
    }

    private fun freshWindows(): List<WindowData> = getFreshWindows(treeParser, accessibilityServiceProvider, nodeCache).windows

    private fun isOnScreen(element: com.danielealbano.androidremotecontrolmcp.services.accessibility.ElementInfo): Boolean {
        val screen = accessibilityServiceProvider.getScreenInfo()
        return element.bounds.right > 0 &&
            element.bounds.bottom > 0 &&
            element.bounds.left < screen.width &&
            element.bounds.top < screen.height
    }

    companion object {
        private const val MAX_AMBIGUOUS_RESULTS = 8
        private const val MAX_DESKTOP_NAVIGATION_ATTEMPTS = 6
        private const val FOREGROUND_CHECK_ATTEMPTS = 8
        private const val FOREGROUND_CHECK_DELAY_MS = 300L
        private const val HOME_SETTLE_DELAY_MS = 450L
        private const val SEARCH_OPEN_DELAY_MS = 500L
        private const val SEARCH_RESULTS_DELAY_MS = 700L
        private const val DESKTOP_SWIPE_DURATION_MS = 450L
        private const val DESKTOP_SWIPE_SETTLE_MS = 350L
        private const val WECHAT_PICKER_DELAY_MS = 500L
        private const val DEFAULT_APP_SELECTION_DELAY_MS = 350L
        private const val SWIPE_START_LOW = 0.16f
        private const val SWIPE_START_HIGH = 0.84f
        private const val SWIPE_Y = 0.52f

        private const val WECHAT_PACKAGE = "com.tencent.mm"
        private const val VIVO_CLONE_PICKER_PACKAGE = "com.vivo.doubleinstance"
        private const val VIVO_PRIMARY_WECHAT_ID = "com.vivo.doubleinstance:id/main"
        private const val VIVO_DESKTOP_SEARCH_ICON_ID = "com.vivo.puresearch:id/search_icon_layout"
        private const val VIVO_SEARCH_FIELD_ID = "com.vivo.browser:id/edit"
        private const val VIVO_OPEN_BUTTON_DESCRIPTION = "打开"
        private val DEFAULT_APP_ALWAYS_LABELS = listOf("始终", "总是", "永久")
        private val DEFAULT_APP_ONCE_LABELS = listOf("仅此一次", "仅一次")
    }
}
