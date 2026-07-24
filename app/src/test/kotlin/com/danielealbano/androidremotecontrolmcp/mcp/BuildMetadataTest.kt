package com.danielealbano.androidremotecontrolmcp.mcp

import com.danielealbano.androidremotecontrolmcp.BuildConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class BuildMetadataTest {
    @Test
    fun `build metadata is present and tool count matches registrations`() {
        val commitIsValid =
            BuildConfig.GIT_COMMIT == "unknown" ||
                BuildConfig.GIT_COMMIT.matches(Regex("[0-9a-f]{40}"))
        assertTrue(commitIsValid)
        Instant.parse(BuildConfig.BUILD_TIME_UTC)
        assertEquals(66, BuildConfig.MCP_TOOL_COUNT)
    }
}
