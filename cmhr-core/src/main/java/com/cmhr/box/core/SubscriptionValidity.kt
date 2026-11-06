package com.cmhr.box.core

object SubscriptionValidity {
    enum class Kind { UNKNOWN, UNLIMITED, EXPIRED, ACTIVE }

    data class State(val kind: Kind, val daysRemaining: Int = 0)

    fun calculate(expire: Long, expiryKnown: Boolean, now: Long): State {
        if (!expiryKnown) return State(Kind.UNKNOWN)
        if (expire <= 0L) return State(Kind.UNLIMITED)
        if (expire <= now) return State(Kind.EXPIRED)
        val remaining = expire - now
        val days = remaining / 86400L + if (remaining % 86400L > 0L) 1L else 0L
        return State(Kind.ACTIVE, days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }
}
