package com.cmhr.box.core

import org.junit.Assert.*
import org.junit.Test

class AppAccessTest {
    private val fallback = "https://fallback.example.invalid/releases"

    // Ported from 1.x AppUpdateUrlTest.
    @Test fun preservesTheConfiguredHttpsDownloadInsteadOfFallingBack() {
        val download = "https://download.example.invalid/client.apk?version=1.4"
        assertEquals(download, AppAccess.resolveUpdateUrl(download, fallback))
        assertEquals(download, AppAccess.resolveUpdateUrl("  $download  ", fallback))
    }

    @Test fun invalidOrInsecureAddressesUseTheFallback() {
        for (value in listOf("", "not a url", "http://download.example.invalid/app.apk", "https:///app.apk")) {
            assertEquals(fallback, AppAccess.resolveUpdateUrl(value, fallback))
        }
    }

    @Test fun serviceDisabledWinsOverUpdate() {
        val issue = AppAccess.evaluate(control(enabled = false, min = 999), 100, fallback)
        assertEquals(AppAccessIssue.ServiceDisabled("off"), issue)
    }

    @Test fun updateOnlyWhenMinVersionIsAboveOurs() {
        assertNull(AppAccess.evaluate(control(min = 100), 100, fallback))
        assertNull(AppAccess.evaluate(control(min = 25), 100, fallback))
        assertEquals(
            AppAccessIssue.UpdateRequired("update", fallback),
            AppAccess.evaluate(control(min = 101, url = "http://insecure.example.invalid"), 100, fallback),
        )
    }

    @Test fun withoutAFallbackAnInvalidLinkBecomesBlank() {
        assertEquals("", AppAccess.resolveUpdateUrl("http://insecure.example.invalid", ""))
    }

    @Test fun missingControlAllowsAccess() {
        assertNull(AppAccess.evaluate(null, 100, fallback))
    }

    private fun control(enabled: Boolean = true, min: Int = 0, url: String = "") =
        AppControl(enabled, min, url, "update", "off")
}
