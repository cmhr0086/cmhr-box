package com.cmhr.box.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.cmhr.box.AppGraph
import com.cmhr.box.R
import com.cmhr.box.core.Announcement
import com.cmhr.box.core.CmhrException
import com.cmhr.box.core.NodeDisplay
import com.cmhr.box.core.SubscriptionExpiryProgress
import com.cmhr.box.core.SubscriptionStatus
import com.cmhr.box.engine.api.EngineState
import com.cmhr.box.engine.api.Node
import com.cmhr.box.engine.api.PerAppMode
import com.cmhr.box.engine.api.PerAppProxy
import com.cmhr.box.engine.api.RoutingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.DrawerLayoutContainer
import org.telegram.ui.ActionBar.MenuDrawable
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ActionBar.ThemeDescription
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RadialProgressView
import org.telegram.tgui.R as TR

/**
 * Connect screen (root): a pinned announcement bar under the ActionBar, the paper-plane button in
 * the middle, the node card, and one card for routing, per-app proxy and subscription.
 */
class HomeFragment : BaseFragment() {

    private val engine get() = AppGraph.engine
    private val cmhr get() = AppGraph.cmhr

    private var scope: CoroutineScope? = null
    private var engineState: EngineState = EngineState.Stopped
    private var nodes: List<Node> = emptyList()
    private var selectedId: String? = null
    private var routing = RoutingMode.BYPASS_MAINLAND
    private var perApp = PerAppProxy(PerAppMode.OFF, emptySet())
    private var announcement: Announcement? = null
    private var announcementShown = false
    private var connecting = false
    private var accessJob: Job? = null
    private var announcementJob: Job? = null
    private var invitePrompted = false
    private var drawer: CmhrDrawer? = null

    // Views (createView may run again; state above survives it).
    private var button: ConnectButton? = null
    private var ring: RadialProgressView? = null
    private var title: TextView? = null
    private var subtitle: TextView? = null
    private var pinned: PinnedBar? = null
    private var pinnedShadow: View? = null
    private var card: LinearLayout? = null
    private var nodeCard: FrameLayout? = null
    private var nodeLabel: TextView? = null
    private var nodeName: TextView? = null
    private var nodeDelay: TextView? = null
    private var nodeArrow: ImageView? = null
    private var routingRow: TextCell? = null
    private var perAppRow: TextCell? = null
    private var subscriptionRow: TextCell? = null

    override fun onFragmentCreate(): Boolean {
        val newScope = MainScope()
        scope = newScope
        newScope.launch {
            engine.state.collect {
                engineState = it
                if (it !is EngineState.Starting) connecting = false
                render()
            }
        }
        newScope.launch {
            reload()
            if (cmhr.isActivated) refreshQuietly()
        }
        loadAnnouncement()
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        scope?.cancel()
        scope = null
        super.onFragmentDestroy()
    }

    override fun createView(context: Context): View {
        actionBar.setBackButtonDrawable(MenuDrawable())
        actionBar.setBackButtonContentDescription(context.getString(R.string.cmhr_menu))
        actionBar.setAllowOverlayTitle(true)
        actionBar.setTitle(context.getString(R.string.cmhr_app_name))
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1) drawerContainer()?.openDrawer(false)
            }
        })

        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        // Pinned announcement, as in a Telegram chat: tap opens it, the cross hides it until the next one.
        pinned = PinnedBar(context).apply {
            setOnClickListener { announcement?.let { openAnnouncement(it) } }
            close.contentDescription = context.getString(R.string.cmhr_hide_announcement)
            close.setOnClickListener {
                announcement?.let { cmhr.hide(it) }
                render()
            }
        }
        root.addView(pinned, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48))

        // Upper part: the button, as large as the screen width allows, with status under it.
        val top = FrameLayout(context)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        // As large as the width allows, but small enough that status and the card still fit below.
        val screenDp = AndroidUtilities.displaySize.y / AndroidUtilities.density
        val spareDp = screenDp - ActionBar.getCurrentActionBarHeight() / AndroidUtilities.density - 48 - 68 - 12 - CARD_ROWS * 50 - 40 - 110
        val size = minOf(minOf(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.58f / AndroidUtilities.density, spareDp)
            .toInt().coerceIn(140, 280)
        val ringFrame = FrameLayout(context)
        ring = RadialProgressView(context).apply {
            setSize(AndroidUtilities.dp(size + 14f))
            setStrokeWidth(3f)
        }
        ringFrame.addView(ring, LayoutHelper.createFrame(size + 24, size + 24, Gravity.CENTER))
        button = ConnectButton(context).apply {
            setOnClickListener { onConnectClick() }
            background = Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_TO_BOUND_EDGE)
        }
        ringFrame.addView(button, LayoutHelper.createFrame(size, size, Gravity.CENTER))
        column.addView(ringFrame, LayoutHelper.createLinear(size + 24, size + 24))
        title = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24f)
            typeface = AndroidUtilities.bold()
            gravity = Gravity.CENTER
        }
        column.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, 18f, 0f, 0f))
        subtitle = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        column.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 32f, 4f, 32f, 0f))
        top.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER, 0f, 0f, 0f, 0f))
        // The pinned bar's shadow floats over the content, as in a chat.
        pinnedShadow = View(context).apply { setBackgroundResource(TR.drawable.header_shadow) }
        top.addView(pinnedShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.TOP))
        root.addView(top, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f))

        // Lower part: the node card, then one rounded card of settings rows.
        val nodeFrame = FrameLayout(context).apply {
            setOnClickListener { scope?.let { s -> showNodesSheet(s) { s.launch { reload() } } } }
        }
        nodeLabel = TextView(context).apply {
            text = context.getString(R.string.cmhr_current_node)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
        }
        nodeFrame.addView(nodeLabel, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.TOP, 16f, 11f, 0f, 0f))
        nodeName = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f)
            typeface = AndroidUtilities.bold()
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        nodeFrame.addView(nodeName, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.TOP, 16f, 32f, 110f, 0f))
        nodeDelay = TextView(context).apply { setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f) }
        nodeFrame.addView(nodeDelay, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.END or Gravity.CENTER_VERTICAL, 0f, 0f, 40f, 0f))
        nodeArrow = ImageView(context).apply {
            setImageResource(TR.drawable.msg_arrowright)
            scaleType = ImageView.ScaleType.CENTER
        }
        nodeFrame.addView(nodeArrow, LayoutHelper.createFrame(24, 24f, Gravity.END or Gravity.CENTER_VERTICAL, 0f, 0f, 12f, 0f))
        nodeCard = nodeFrame
        root.addView(nodeFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 68, 16f, 0f, 16f, 12f))

        val rows = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipToOutline = true
        }
        fun row(onClick: () -> Unit) = TextCell(context).also {
            it.setOnClickListener { onClick() }
            rows.addView(it, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50))
        }
        routingRow = row { showRoutingSheet(routing) { setRouting(it) } }
        perAppRow = row { presentFragment(PerAppFragment()) }
        subscriptionRow = row {
            presentFragment(if (cmhr.isActivated) SubscriptionFragment() else InviteFragment.create(replace = false))
        }
        card = rows
        root.addView(rows, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16f, 0f, 16f, 24f))

        fragmentView = root
        setUpDrawer(context)
        render()
        return root
    }

    override fun onResume() {
        super.onResume()
        drawerContainer()?.setAllowOpenDrawer(true, false)
        scope?.launch {
            reload()
            if (cmhr.refreshDue()) refreshQuietly()
        }
        if (announcement == null) loadAnnouncement()
        checkAccess()
        if (cmhr.subscriptionEnabled && !cmhr.isActivated && !invitePrompted) {
            invitePrompted = true
            // First launch: open activation once; the button offers it again later.
            AndroidUtilities.runOnUIThread({ presentFragment(InviteFragment.create(replace = false)) }, 300)
        }
    }

    override fun onPause() {
        super.onPause()
        drawerContainer()?.apply {
            setAllowOpenDrawer(false, false)
            setAllowOpenDrawerBySwipe(true)
        }
    }

    // The root screen has nothing to go back to: an edge swipe opens the menu instead.
    override fun isSwipeBackEnabled(event: MotionEvent?): Boolean = false

    private fun drawerContainer(): DrawerLayoutContainer? = parentLayout?.drawerLayoutContainer

    private fun setUpDrawer(context: Context) {
        val container = drawerContainer() ?: return
        val menu = container.drawerLayout as? CmhrDrawer ?: CmhrDrawer(context).also { it.attachTo(container) }
        menu.onItemClick = { id ->
            container.closeDrawer(false)
            when (id) {
                DRAWER_SUBSCRIPTION -> presentFragment(SubscriptionFragment())
                DRAWER_ABOUT -> presentFragment(AboutFragment())
            }
        }
        drawer = menu
        updateDrawer()
    }

    private fun updateDrawer() {
        val context = parentActivity ?: return
        val status = when {
            !cmhr.subscriptionEnabled -> context.getString(R.string.cmhr_state_not_configured)
            !cmhr.isActivated -> context.getString(R.string.cmhr_state_not_activated)
            else -> context.getString(R.string.cmhr_invite_status_active)
        }
        val items = buildList {
            add(CmhrDrawer.Item(DRAWER_CONNECT, R.drawable.cmhr_ic_plane, context.getString(R.string.cmhr_drawer_connect)))
            if (cmhr.subscriptionEnabled) {
                add(CmhrDrawer.Item(DRAWER_SUBSCRIPTION, TR.drawable.msg_calendar2, context.getString(R.string.cmhr_section_subscription)))
            }
            add(CmhrDrawer.Item(DRAWER_ABOUT, TR.drawable.msg_info, context.getString(R.string.cmhr_section_about)))
        }
        drawer?.setContent(
            context.getString(R.string.cmhr_app_name), status,
            PlaneDrawable(context, Theme.getColor(Theme.key_chats_menuName), fill = 0.62f), items,
        )
    }

    private suspend fun reload() {
        nodes = engine.nodes()
        selectedId = engine.selectedNodeId()
        routing = engine.routingMode()
        perApp = engine.perAppProxy()
        render()
    }

    private fun setRouting(mode: RoutingMode) {
        routing = mode
        render()
        scope?.launch { engine.setRoutingMode(mode) }
    }

    /** A new or edited announcement opens once; the pinned bar reopens it. */
    private fun loadAnnouncement() {
        if (announcementJob?.isActive == true) return
        announcementJob = scope?.launch {
            // A failed fetch (null) never replaces one already loaded.
            val item = cmhr.announcement() ?: return@launch
            announcement = item
            render()
            if (cmhr.isUnread(item) && !announcementShown) {
                announcementShown = true
                openAnnouncement(item)
            }
        }
    }

    private fun openAnnouncement(item: Announcement) {
        showAnnouncementSheet(item) { cmhr.markRead(item) }
    }

    private fun checkAccess() {
        if (accessJob?.isActive == true) return
        accessJob = scope?.launch {
            val issue = cmhr.checkAppAccess() ?: return@launch
            if (engine.state.value is EngineState.Running) engine.stop()
            showAccessDialog(issue)
        }
    }

    private suspend fun refreshQuietly() {
        runCatching { cmhr.refresh() }
            .onSuccess { if (it is SubscriptionStatus.Removed) showRemovedDialog(it.reason) }
            .onFailure { if (it is CmhrException.AccessDenied) showAccessDialog(it.issue) }
        reload()
    }

    private fun onConnectClick() {
        when {
            !cmhr.subscriptionEnabled && nodes.isEmpty() -> Unit
            !cmhr.isActivated && cmhr.subscriptionEnabled -> presentFragment(InviteFragment.create(replace = false))
            engineState is EngineState.Running || engineState is EngineState.Starting ->
                scope?.launch { engine.stop() }
            else -> connect()
        }
    }

    private fun connect() {
        val activity = parentActivity ?: return
        if (connecting) return
        connecting = true
        render()
        scope?.launch {
            val ready = if (cmhr.isActivated) {
                runCatching { cmhr.ensureReadyForConnection() }
                    .onFailure { error ->
                        if (error is CmhrException.AccessDenied) showAccessDialog(error.issue)
                        else activity.toast(activity.describeError(error))
                    }
                    .getOrNull()
            } else {
                SubscriptionStatus.Ready(nodes.size, needsSelection = selectedId == null)
            }
            when {
                ready == null -> Unit
                ready is SubscriptionStatus.Removed -> showRemovedDialog(ready.reason)
                ready is SubscriptionStatus.Ready && ready.needsSelection -> scope?.let { s -> showNodesSheet(s) { s.launch { reload() } } }
                else -> {
                    requestNotifications(activity)
                    val prepare = engine.prepareIntent(activity)
                    if (prepare != null) startActivityForResult(prepare, REQUEST_VPN) else engine.start()
                    return@launch
                }
            }
            connecting = false
            reload()
        }
    }

    override fun onActivityResultFragment(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_VPN) return
        if (resultCode == Activity.RESULT_OK) {
            scope?.launch { engine.start() }
        } else {
            connecting = false
            render()
            parentActivity?.let { it.toast(it.getString(R.string.cmhr_vpn_denied)) }
        }
    }

    private fun requestNotifications(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun heroState(context: Context): HeroState {
        val nodeName = nodes.firstOrNull { it.id == selectedId }?.name.orEmpty()
        return when {
            !cmhr.subscriptionEnabled && nodes.isEmpty() -> HeroState(
                context.getString(R.string.cmhr_state_not_configured),
                context.getString(R.string.cmhr_not_configured_hint),
                Theme.key_windowBackgroundWhiteGrayIcon, busy = false, filled = false, enabled = false,
            )
            !cmhr.isActivated && cmhr.subscriptionEnabled -> HeroState(
                context.getString(R.string.cmhr_state_not_activated),
                context.getString(R.string.cmhr_tap_to_activate),
                Theme.key_featuredStickers_addButton, busy = false, filled = false,
            )
            engineState is EngineState.Running -> HeroState(
                context.getString(R.string.cmhr_state_running),
                nodeName.ifEmpty { context.getString(R.string.cmhr_tap_to_disconnect) },
                Theme.key_windowBackgroundWhiteGreenText2, busy = false, filled = true,
            )
            engineState is EngineState.Starting || connecting -> HeroState(
                context.getString(R.string.cmhr_state_starting), nodeName,
                Theme.key_featuredStickers_addButton, busy = true, filled = true,
            )
            engineState is EngineState.Error -> HeroState(
                context.getString(R.string.cmhr_state_error),
                (engineState as EngineState.Error).message,
                Theme.key_text_RedBold, busy = false, filled = false,
            )
            else -> HeroState(
                context.getString(R.string.cmhr_state_stopped),
                context.getString(R.string.cmhr_tap_to_connect),
                Theme.key_featuredStickers_addButton, busy = false, filled = false,
            )
        }
    }

    /** Pushes state into the views; safe before createView and after it reran. */
    private fun render() {
        val context = parentActivity ?: return
        updateDrawer()
        val state = heroState(context)
        val accent = Theme.getColor(state.accentKey)
        fragmentView?.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        button?.apply {
            this.accent = accent
            filled = state.filled
            isEnabled = state.enabled
            contentDescription = "${state.title}, ${state.subtitle}"
            invalidate()
        }
        ring?.apply {
            setProgressColor(accent)
            visibility = if (state.busy) View.VISIBLE else View.INVISIBLE
        }
        title?.apply {
            text = state.title
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        }
        subtitle?.apply {
            text = state.subtitle
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2))
        }

        val item = announcement
        val pinnedVisible = item != null && !cmhr.isHidden(item)
        pinned?.apply {
            visibility = if (pinnedVisible) View.VISIBLE else View.GONE
            if (item != null) bind(
                item.title.ifBlank { context.getString(R.string.cmhr_pinned_announcement) },
                item.content.trim().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
            )
        }
        pinnedShadow?.visibility = if (pinnedVisible) View.VISIBLE else View.GONE

        val hasNodes = cmhr.isActivated || nodes.isNotEmpty()
        card?.apply {
            visibility = if (hasNodes || cmhr.subscriptionEnabled) View.VISIBLE else View.GONE
            background = Theme.createRoundRectDrawable(AndroidUtilities.dp(12f), Theme.getColor(Theme.key_windowBackgroundGray))
        }
        val selected = nodes.firstOrNull { it.id == selectedId }
        nodeCard?.apply {
            visibility = if (hasNodes) View.VISIBLE else View.GONE
            background = Theme.createRoundRectDrawable(AndroidUtilities.dp(12f), Theme.getColor(Theme.key_windowBackgroundGray))
        }
        nodeLabel?.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2))
        nodeName?.apply {
            text = selected?.let { NodeDisplay.name(it.name, it.name, nodes.map { n -> n.name }) }
                ?: context.getString(R.string.cmhr_node_unselected)
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        }
        nodeDelay?.apply {
            text = context.delayText(selected?.delayMs)
            setTextColor(Theme.getColor(delayColorKey(selected?.delayMs)))
        }
        nodeArrow?.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon))
        routingRow?.bindRow(context.getString(R.string.cmhr_routing), context.getString(routing.title()), TR.drawable.msg_language, divider = true)
        perAppRow?.bindRow(
            context.getString(R.string.cmhr_per_app),
            if (perApp.mode == PerAppMode.OFF) context.getString(perApp.mode.title())
            else context.getString(R.string.cmhr_per_app_count, context.getString(perApp.mode.title()), perApp.packages.size),
            TR.drawable.msg_folders, divider = cmhr.subscriptionEnabled,
        )
        subscriptionRow?.apply {
            visibility = if (cmhr.subscriptionEnabled) View.VISIBLE else View.GONE
            bindRow(context.getString(R.string.cmhr_section_subscription), context.subscriptionSummary(), TR.drawable.msg_calendar2, divider = false)
        }
    }

    private fun TextCell.bindRow(text: CharSequence, value: CharSequence, icon: Int, divider: Boolean) {
        setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText)
        setTextAndValueAndIcon(text, value, icon, divider)
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText))
        background = Theme.getSelectorDrawable(false)
    }

    /** "120 GB left · 23 days" in the row; the subscription page has the full numbers. */
    private fun Context.subscriptionSummary(): String {
        if (!cmhr.isActivated) return getString(R.string.cmhr_state_not_activated)
        val usage = cmhr.usage()
        val expiry = cmhr.expiryProgress()
        val traffic = if (usage.total > 0L) getString(R.string.cmhr_summary_traffic_left, formatBytes(usage.remaining.coerceAtLeast(0L))) else null
        val validity = when (expiry.kind) {
            SubscriptionExpiryProgress.Kind.ACTIVE -> resources.getQuantityString(R.plurals.cmhr_summary_days, expiry.daysRemaining, expiry.daysRemaining)
            SubscriptionExpiryProgress.Kind.UNLIMITED -> getString(R.string.cmhr_validity_unlimited)
            SubscriptionExpiryProgress.Kind.EXPIRED -> getString(R.string.cmhr_validity_expired)
            SubscriptionExpiryProgress.Kind.UNKNOWN -> null
        }
        return listOfNotNull(traffic, validity).joinToString(" · ")
    }

    override fun getThemeDescriptions(): ArrayList<ThemeDescription> {
        val recolor = object : ThemeDescription.ThemeDescriptionDelegate {
            override fun didSetColor() {
                render()
                drawer?.applyColors()
            }
        }
        return arrayListOf(
            ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
            ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
            ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
            ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),
            ThemeDescription(null, 0, null, null, null, recolor, Theme.key_windowBackgroundWhite),
            ThemeDescription(null, 0, null, null, null, recolor, Theme.key_chats_menuBackground),
        )
    }

    private companion object {
        const val CARD_ROWS = 3
        const val DRAWER_CONNECT = 1
        const val DRAWER_SUBSCRIPTION = 2
        const val DRAWER_ABOUT = 3
        const val REQUEST_VPN = 1001
        const val REQUEST_NOTIFICATIONS = 1002
    }
}
