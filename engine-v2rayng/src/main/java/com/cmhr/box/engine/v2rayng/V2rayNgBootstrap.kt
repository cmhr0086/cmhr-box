package com.cmhr.box.engine.v2rayng

import android.app.Application
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.work.Configuration
import androidx.work.WorkManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager

/**
 * Replaces AngApplication's initialization; the host Application calls this in every process
 * (:daemon, :tasks and :bg run upstream services that read MMKV).
 *
 * Not done here, on purpose:
 * - AngApplication.application stays unset. It is typed AngApplication (a final class), so the
 *   host cannot be assigned. Only upstream UI paths read it (SubscriptionUpdater default
 *   arguments via SubEditActivity, SubscriptionsViewModel, MainRepository); none are launched.
 *   Never call SettingsManager.removeSubscriptionWithDefault or SubscriptionUpdater without an
 *   explicit context.
 * - ThemeManager.refresh(): only upstream Compose screens read it.
 */
object V2rayNgBootstrap {

    /** Same as AngApplication.attachBaseContext: apply the per-app language. */
    fun wrapBaseContext(base: Context): Context = ContextCompat.getContextForLanguage(base)

    fun init(app: Application) {
        MmkvManager.initialize(app)
        AppLocaleManager.initialize(app)
        // The manifest from :v2rayng removes WorkManagerInitializer; initialize it like upstream.
        WorkManager.initialize(
            app,
            Configuration.Builder()
                .setDefaultProcessName("${AppConfig.ANG_PACKAGE}:bg")
                .build()
        )
        SettingsManager.initApp(app)
    }
}
