package com.cmhr.box.core

import android.content.Context
import android.content.SharedPreferences

/** Local business state. Only the invite code is a credential; the subscription URL is never stored. */
interface CmhrStore {
    var inviteCode: String?
    var subscriptionUserinfo: String?
    var subscriptionUpdatedAt: Long

    /** Set once the first-run flow ended; never reset, so a removed subscription does not repeat it. */
    var onboardingDone: Boolean

    /** Key of the last announcement the user acknowledged. */
    var seenAnnouncement: String?

    /** Last announcement the service returned (JSON), shown at once on the next launch. */
    var cachedAnnouncement: String?

    fun clearSubscription() {
        subscriptionUserinfo = null
        subscriptionUpdatedAt = 0L
    }
}

/** Main-process store; upstream MMKV stays reserved for v2rayNG's own data. */
class SharedPreferencesCmhrStore(context: Context) : CmhrStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("cmhr", Context.MODE_PRIVATE)

    override var inviteCode: String?
        get() = prefs.getString(KEY_INVITE, null)
        set(value) = prefs.edit().putString(KEY_INVITE, value).apply()

    override var subscriptionUserinfo: String?
        get() = prefs.getString(KEY_USERINFO, null)
        set(value) = prefs.edit().putString(KEY_USERINFO, value).apply()

    override var subscriptionUpdatedAt: Long
        get() = prefs.getLong(KEY_UPDATED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_UPDATED_AT, value).apply()

    override var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    override var seenAnnouncement: String?
        get() = prefs.getString(KEY_SEEN_ANNOUNCEMENT, null)
        set(value) = prefs.edit().putString(KEY_SEEN_ANNOUNCEMENT, value).apply()

    override var cachedAnnouncement: String?
        get() = prefs.getString(KEY_CACHED_ANNOUNCEMENT, null)
        set(value) = prefs.edit().putString(KEY_CACHED_ANNOUNCEMENT, value).apply()

    private companion object {
        const val KEY_INVITE = "invite_code"
        const val KEY_USERINFO = "subscription_userinfo"
        const val KEY_UPDATED_AT = "subscription_updated_at"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_SEEN_ANNOUNCEMENT = "seen_announcement"
        const val KEY_CACHED_ANNOUNCEMENT = "cached_announcement"
    }
}
