package com.cmhr.box.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionValidityTest {
    private val now = 1000000L
    private val day = 86400L

    private fun state(expire: Long, known: Boolean = true) = SubscriptionValidity.calculate(expire, known, now)

    @Test fun unknownExpiryStaysUnknown() {
        assertEquals(SubscriptionValidity.Kind.UNKNOWN, state(now + day, known = false).kind)
    }

    @Test fun zeroExpiryIsUnlimited() {
        assertEquals(SubscriptionValidity.Kind.UNLIMITED, state(0L).kind)
    }

    @Test fun pastOrCurrentDeadlineIsExpired() {
        assertEquals(SubscriptionValidity.Kind.EXPIRED, state(now).kind)
        assertEquals(SubscriptionValidity.Kind.EXPIRED, state(now - 1).kind)
    }

    @Test fun partialDaysRoundUp() {
        assertEquals(SubscriptionValidity.State(SubscriptionValidity.Kind.ACTIVE, 90), state(now + 90 * day))
        assertEquals(SubscriptionValidity.State(SubscriptionValidity.Kind.ACTIVE, 1), state(now + 1))
        assertEquals(SubscriptionValidity.State(SubscriptionValidity.Kind.ACTIVE, 2), state(now + day + 1))
    }
}
