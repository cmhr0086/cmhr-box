package com.cmhr.box.core

/** Parsed `subscription-userinfo` header (`upload=; download=; total=; expire=`), seconds for expire. */
data class UsageInfo(
    val upload: Long = 0L,
    val download: Long = 0L,
    val total: Long = 0L,
    val expire: Long = 0L,
    val expiryKnown: Boolean = false,
) {
    val used: Long get() = upload + download
    val remaining: Long get() = total - used

    fun isExpired(nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean =
        expire > 0L && expire <= nowSeconds

    val isExhausted: Boolean
        get() = total > 0L && used >= total

    companion object {
        /** Ported from 1.x PrivateSubscriptionManager.parseUsageInfo, including expiryKnown. */
        fun parse(subscriptionUserinfo: String?): UsageInfo {
            if (subscriptionUserinfo.isNullOrBlank()) return UsageInfo()

            val expiryField = "(?:^|;)\\s*expire\\s*=\\s*([^;]*)".toRegex()
                .find(subscriptionUserinfo)
            val expiry = expiryField?.groupValues?.get(1)?.trim()?.toLongOrNull()
            val knownStats = "(?:^|;)\\s*(?:upload|download|total)=[0-9]+".toRegex()
                .containsMatchIn(subscriptionUserinfo)

            fun get(key: String): Long {
                return "$key=([0-9]+)".toRegex().find(subscriptionUserinfo)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toLongOrNull()
                    ?: 0L
            }

            return UsageInfo(
                upload = get("upload"),
                download = get("download"),
                total = get("total"),
                expire = expiry ?: 0L,
                expiryKnown = if (expiryField != null) expiry != null && expiry >= -1L else knownStats,
            )
        }
    }
}
