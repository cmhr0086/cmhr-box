package com.cmhr.box.core

import org.junit.Assert.*
import org.junit.Test

class UsageInfoTest {
    @Test fun unlimitedValuesAndOmittedExpiryAreKnown() {
        for (header in listOf("expire=0", "expire=-1", "upload=1; download=2; total=0")) {
            val info = UsageInfo.parse(header)
            assertTrue(header, info.expiryKnown)
            assertTrue(info.expire <= 0L)
        }
    }

    @Test fun missingOrMalformedMetadataDoesNotClaimUnlimitedValidity() {
        for (header in listOf(null, "", "nonsense", "upload=1; expire=broken", "expire=-2")) {
            assertFalse(UsageInfo.parse(header).expiryKnown)
        }
    }

    @Test fun finiteExpiryKeepsItsActualTimestamp() {
        val info = UsageInfo.parse("upload=1; expire=1900000000; total=100")
        assertTrue(info.expiryKnown)
        assertEquals(1900000000L, info.expire)
    }
}
