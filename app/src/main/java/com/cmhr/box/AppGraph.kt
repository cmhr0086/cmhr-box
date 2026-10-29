package com.cmhr.box

import android.app.Application
import com.cmhr.box.core.CmhrApi
import com.cmhr.box.core.CmhrConfig
import com.cmhr.box.core.CmhrService
import com.cmhr.box.core.SharedPreferencesCmhrStore
import com.cmhr.box.engine.api.ProxyEngine
import com.cmhr.box.engine.v2rayng.V2rayNgEngine

/** Manual DI. The only place in :app that references engine-v2rayng. */
object AppGraph {
    private lateinit var app: Application

    fun init(app: Application) {
        this.app = app
    }

    /** Main process only: the engine listens for daemon broadcasts there. */
    val engine: ProxyEngine by lazy { V2rayNgEngine(app) }

    val cmhr: CmhrService by lazy {
        CmhrService(
            CmhrConfig(
                subscriptionEndpoint = BuildConfig.CMHR_SUBSCRIPTION_ENDPOINT,
                announcementEndpoint = BuildConfig.CMHR_ANNOUNCEMENT_ENDPOINT,
                appControlEndpoint = BuildConfig.CMHR_APP_CONTROL_ENDPOINT,
                versionCode = BuildConfig.VERSION_CODE,
                defaultUpdateUrl = BuildConfig.CMHR_DEFAULT_UPDATE_URL,
            ),
            CmhrApi(BuildConfig.VERSION_CODE),
            SharedPreferencesCmhrStore(app),
            engine,
        )
    }
}
