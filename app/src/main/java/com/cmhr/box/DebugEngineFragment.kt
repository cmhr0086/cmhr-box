package com.cmhr.box

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.cmhr.box.engine.api.EngineState
import com.cmhr.box.engine.api.Node
import com.cmhr.box.engine.api.ProxyEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ActionBar.ThemeDescription
import java.net.HttpURLConnection
import java.net.URL

/**
 * Stage 2 debug page: paste a subscription URL, import, pick a node, connect, test delay.
 * Temporary; removed when the real screens land in stage 4 (strings are not localized on purpose).
 */
class DebugEngineFragment : BaseFragment() {

    private lateinit var engine: ProxyEngine
    private var scope: CoroutineScope? = null
    private var stateJob: Job? = null

    private var nodes: List<Node> = emptyList()
    private var selectedId: String? = null
    private var engineState: EngineState = EngineState.Stopped
    private var message: String = ""

    private var root: ScrollView? = null
    private var urlInput: EditText? = null
    private var statusView: TextView? = null
    private var toggleButton: Button? = null
    private var nodeList: LinearLayout? = null

    override fun onFragmentCreate(): Boolean {
        engine = AppGraph.engine
        val newScope = MainScope()
        scope = newScope
        stateJob = newScope.launch {
            engine.state.collect {
                engineState = it
                render()
            }
        }
        newScope.launch { reloadNodes() }
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        scope?.cancel()
        scope = null
        super.onFragmentDestroy()
    }

    override fun createView(context: Context): View {
        actionBar.setTitle("CMHR-Box 引擎调试")

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = AndroidUtilities.dp(16f)
            setPadding(pad, pad, pad, pad)
        }
        urlInput = EditText(context).apply {
            hint = "订阅链接（https://…）"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText))
        }
        column.addView(urlInput)
        column.addView(button(context, "拉取并导入") { importSubscription() })
        column.addView(button(context, "全部测速") { testAll() })
        toggleButton = button(context, "连接") { toggle() }
        column.addView(toggleButton)
        statusView = text(context, 14f)
        column.addView(statusView)
        nodeList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(nodeList)

        val scroll = ScrollView(context)
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        scroll.addView(column)
        root = scroll
        fragmentView = scroll
        render()
        return scroll
    }

    private fun importSubscription() {
        val url = urlInput?.text?.toString()?.trim().orEmpty()
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            showMessage("请输入 http(s) 订阅链接")
            return
        }
        scope?.launch {
            showMessage("拉取中…")
            runCatching {
                val body = withContext(Dispatchers.IO) { fetch(url) }
                engine.replaceManagedSubscription(body)
            }.onSuccess { imported ->
                showMessage("已导入 ${imported.size} 个节点")
                reloadNodes()
            }.onFailure {
                showMessage("导入失败：${it.message ?: it.javaClass.simpleName}")
            }
        }
    }

    private fun testAll() {
        val ids = nodes.map { it.id }
        if (ids.isEmpty()) return
        scope?.launch {
            showMessage("测速中…")
            val result = engine.testDelay(ids)
            val ok = result.values.count { it > 0 }
            showMessage("测速完成：$ok/${ids.size} 可用")
            reloadNodes()
        }
    }

    private fun toggle() {
        val activity = parentActivity ?: return
        when (engineState) {
            is EngineState.Running, EngineState.Starting -> scope?.launch { engine.stop() }
            else -> {
                if (selectedId == null) {
                    showMessage("请先选择节点")
                    return
                }
                requestNotificationPermission(activity)
                val prepare = engine.prepareIntent(activity)
                if (prepare != null) {
                    startActivityForResult(prepare, REQUEST_VPN)
                } else {
                    scope?.launch { engine.start() }
                }
            }
        }
    }

    override fun onActivityResultFragment(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_VPN) {
            if (resultCode == Activity.RESULT_OK) {
                scope?.launch { engine.start() }
            } else {
                showMessage("未授予 VPN 权限")
            }
        }
    }

    private fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private suspend fun reloadNodes() {
        nodes = engine.nodes()
        selectedId = engine.selectedNodeId()
        render()
    }

    private fun showMessage(text: String) {
        message = text
        render()
    }

    private fun render() {
        val context = parentActivity ?: return
        val status = when (val s = engineState) {
            EngineState.Stopped -> "已断开"
            EngineState.Starting -> "连接中…"
            is EngineState.Running -> "已连接：${nodes.firstOrNull { it.id == s.nodeId }?.name ?: s.nodeId.orEmpty()}"
            is EngineState.Error -> "错误：${s.message}"
        }
        statusView?.text = if (message.isEmpty()) status else "$status\n$message"
        toggleButton?.text = when (engineState) {
            is EngineState.Running, EngineState.Starting -> "断开"
            else -> "连接"
        }
        val list = nodeList ?: return
        list.removeAllViews()
        nodes.forEach { node ->
            val delay = when (val d = node.delayMs) {
                null -> ""
                -1L -> "  超时"
                else -> "  ${d}ms"
            }
            val mark = if (node.id == selectedId) "● " else "○ "
            list.addView(text(context, 16f).apply {
                text = "$mark${node.name}  [${node.protocol}]$delay"
                val pad = AndroidUtilities.dp(10f)
                setPadding(0, pad, 0, pad)
                setOnClickListener {
                    scope?.launch {
                        engine.select(node.id)
                        reloadNodes()
                    }
                }
            })
        }
    }

    private fun button(context: Context, label: String, onClick: () -> Unit) =
        Button(context).apply {
            text = label
            setOnClickListener { onClick() }
        }

    private fun text(context: Context, sizeSp: Float) =
        TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            gravity = Gravity.START
        }

    override fun getThemeDescriptions(): ArrayList<ThemeDescription> = arrayListOf(
        ThemeDescription(root, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),
        ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
        ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
    )

    private companion object {
        const val REQUEST_VPN = 1001
        const val REQUEST_NOTIFICATIONS = 1002

        fun fetch(url: String): String {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                val code = connection.responseCode
                check(code in 200..299) { "HTTP $code" }
                return connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
    }
}
