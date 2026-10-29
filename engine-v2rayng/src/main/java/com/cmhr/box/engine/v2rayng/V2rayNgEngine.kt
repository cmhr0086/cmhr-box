package com.cmhr.box.engine.v2rayng

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.cmhr.box.engine.api.EngineState
import com.cmhr.box.engine.api.Node
import com.cmhr.box.engine.api.ProxyEngine
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.RealPingResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * [ProxyEngine] on top of v2rayNG. Create one instance in the main process only.
 *
 * Managed nodes live in one upstream subscription group with a fixed id and no URL, so the
 * upstream subscription updater skips it; cmhr-core fetches the content and hands it over.
 */
class V2rayNgEngine(context: Context) : ProxyEngine {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<EngineState>(EngineState.Stopped)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private class DelayRequest {
        val results = ConcurrentHashMap<String, Long>()
        val done = CompletableDeferred<Unit>()
    }

    private val delayRequests = ConcurrentHashMap<String, DelayRequest>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Upstream MainViewModel copies geosite/geoip from the libv2ray.aar assets into the user
    // asset folder; without them the core rejects routing rules such as geosite:google.
    private val assetsReady: Deferred<Unit> = scope.async {
        SettingsManager.initAssets(appContext, appContext.assets)
    }

    // Daemon and test service report back through upstream's UI broadcast.
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val safeIntent = intent ?: return
            val requestId = safeIntent.getStringExtra(MessageHelper.EXTRA_REQUEST_ID).orEmpty()
            when (safeIntent.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING,
                AppConfig.MSG_STATE_START_SUCCESS ->
                    _state.value = EngineState.Running(MmkvManager.getSelectServer())

                AppConfig.MSG_STATE_NOT_RUNNING,
                AppConfig.MSG_STATE_STOP_SUCCESS ->
                    _state.value = EngineState.Stopped

                AppConfig.MSG_STATE_START_FAILURE ->
                    _state.value = EngineState.Error(
                        safeIntent.getStringExtra("content").orEmpty().ifEmpty { "start failed" }
                    )

                AppConfig.MSG_MEASURE_CONFIG_SUCCESS ->
                    safeIntent.serializable<RealPingResult>("content")?.let { result ->
                        delayRequests[requestId]?.results?.put(result.guid, result.delayMillis)
                    }

                AppConfig.MSG_MEASURE_CONFIG_FINISH,
                AppConfig.MSG_MEASURE_CONFIG_CANCEL ->
                    delayRequests[requestId]?.done?.complete(Unit)
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
            Utils.receiverFlags()
        )
        // The daemon answers with MSG_STATE_RUNNING / MSG_STATE_NOT_RUNNING if it is alive.
        MessageHelper.sendMsg2Service(appContext, AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override suspend fun replaceManagedSubscription(rawContent: String): List<Node> =
        withContext(Dispatchers.IO) {
            val links = shareLinks(rawContent)
            require(links.isNotEmpty()) { "subscription has no supported nodes" }

            if (MmkvManager.decodeSubscription(MANAGED_SUB_ID) == null) {
                MmkvManager.encodeSubscription(MANAGED_SUB_ID, SubscriptionItem(remarks = MANAGED_SUB_REMARKS))
            }
            // append = false: upstream saves the new profiles first, then drops the old ones.
            val (count, _) = AngConfigManager.importBatchConfig(links, MANAGED_SUB_ID, append = false)
            check(count > 0) { "subscription could not be parsed" }

            val nodes = managedNodes()
            val selected = MmkvManager.getSelectServer()
            if (nodes.none { it.id == selected }) {
                MmkvManager.setSelectServer(nodes.first().id)
            }
            nodes
        }

    override suspend fun clearManagedSubscription() = withContext(Dispatchers.IO) {
        MmkvManager.removeSubscription(MANAGED_SUB_ID)
    }

    override suspend fun nodes(): List<Node> = withContext(Dispatchers.IO) { managedNodes() }

    override suspend fun selectedNodeId(): String? = withContext(Dispatchers.IO) {
        MmkvManager.getSelectServer()?.takeIf { it in MmkvManager.decodeServerList(MANAGED_SUB_ID) }
    }

    override suspend fun select(nodeId: String) {
        withContext(Dispatchers.IO) {
            require(nodeId in MmkvManager.decodeServerList(MANAGED_SUB_ID)) { "unknown node $nodeId" }
            MmkvManager.setSelectServer(nodeId)
        }
        if (_state.value is EngineState.Running) {
            // Upstream restarts the daemon with the newly selected server.
            LauncherManager.restartService(appContext)
        }
    }

    override fun prepareIntent(context: Context): Intent? =
        if (SettingsManager.isVpnMode()) VpnService.prepare(context) else null

    override suspend fun start() {
        _state.value = EngineState.Starting
        assetsReady.await()
        // LauncherManager shows toasts, so it must run on the main thread.
        val started = withContext(Dispatchers.Main) { LauncherManager.startServiceFromToggle(appContext) }
        if (!started) {
            _state.value = EngineState.Error("start failed")
        }
    }

    override suspend fun stop() {
        LauncherManager.stopService(appContext)
    }

    override suspend fun testDelay(nodeIds: List<String>): Map<String, Long> {
        if (nodeIds.isEmpty()) return emptyMap()
        val requestId = UUID.randomUUID().toString()
        val request = DelayRequest()
        delayRequests[requestId] = request
        try {
            withContext(Dispatchers.IO) { MmkvManager.clearAllTestDelayResults(nodeIds) }
            MessageHelper.sendMsg2TestService(
                appContext,
                TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_START, serverGuids = nodeIds),
                requestId
            )
            if (withTimeoutOrNull(DELAY_TEST_TIMEOUT_MS) { request.done.await() } == null) {
                LogUtil.w(AppConfig.TAG, "V2rayNgEngine: delay test timed out")
            }
        } finally {
            delayRequests.remove(requestId)
        }
        return nodeIds.associateWith { id -> request.results[id]?.takeIf { it > 0 } ?: -1L }
    }

    private fun managedNodes(): List<Node> =
        MmkvManager.decodeServerList(MANAGED_SUB_ID).mapNotNull { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
            val delay = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L
            Node(
                id = guid,
                name = profile.remarks,
                protocol = profile.configType.name.lowercase(),
                delayMs = when {
                    delay > 0 -> delay
                    delay < 0 -> -1L
                    else -> null
                },
            )
        }

    companion object {
        private const val MANAGED_SUB_ID = "cmhr-managed"
        private const val MANAGED_SUB_REMARKS = "CMHR"
        private const val DELAY_TEST_TIMEOUT_MS = 120_000L

        private val SHARE_LINK_SCHEMES = listOf(
            AppConfig.VLESS,
            AppConfig.HYSTERIA2,
            AppConfig.HY2,
            AppConfig.VMESS,
            AppConfig.TROJAN,
            AppConfig.SHADOWSOCKS,
        )

        /**
         * Keeps only share links. importBatchConfig also turns any http(s) line into a new
         * upstream subscription and fetches it, which must never happen for managed content.
         */
        internal fun shareLinks(rawContent: String): String {
            val decoded = Utils.decode(rawContent.trim()).ifEmpty { rawContent }
            return decoded.lineSequence()
                .map { it.trim() }
                .filter { line -> SHARE_LINK_SCHEMES.any { line.startsWith(it, ignoreCase = true) } }
                .joinToString("\n")
        }
    }
}
