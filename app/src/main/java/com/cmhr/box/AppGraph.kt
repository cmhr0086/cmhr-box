package com.cmhr.box

import android.app.Application
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
}
