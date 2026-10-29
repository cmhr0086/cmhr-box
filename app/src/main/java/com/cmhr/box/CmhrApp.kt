package com.cmhr.box

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.cmhr.box.engine.v2rayng.V2rayNgBootstrap
import org.telegram.ui.TelegramUI

class CmhrApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(V2rayNgBootstrap.wrapBaseContext(base))
    }

    override fun onCreate() {
        super.onCreate()
        // Upstream services in :daemon, :tasks and :bg need this too.
        V2rayNgBootstrap.init(this)
        AppGraph.init(this)
        if (isMainProcess()) {
            TelegramUI.init(this)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isMainProcess()) {
            TelegramUI.onConfigurationChanged(newConfig)
        }
    }

    private fun isMainProcess(): Boolean = processName() == packageName

    private fun processName(): String =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            runCatching { java.io.File("/proc/self/cmdline").readText().trim('\u0000') }.getOrDefault(packageName)
        }
}
