package com.cmhr.box.engine.api

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

/** Proxy core as seen by the app; implementations hide the upstream client. */
interface ProxyEngine {
    val state: StateFlow<EngineState>

    /**
     * Replaces all managed nodes with the ones parsed from [rawContent] (base64 or plain
     * share links). New nodes are stored before the old ones are removed. The selection moves
     * to the matching new node; without a match the first node is selected.
     */
    suspend fun replaceManagedSubscription(rawContent: String): SubscriptionReplacement

    /** Removes the managed subscription and its nodes. */
    suspend fun clearManagedSubscription()

    suspend fun nodes(): List<Node>

    /** Id of the selected node, or null when none is selected. */
    suspend fun selectedNodeId(): String?

    suspend fun select(nodeId: String)

    /** Wraps VpnService.prepare: non-null means the caller must launch it and wait for RESULT_OK. */
    fun prepareIntent(context: Context): Intent?

    suspend fun start()

    suspend fun stop()

    /** Real delay test; failed nodes map to -1. */
    suspend fun testDelay(nodeIds: List<String>): Map<String, Long>
}

sealed interface EngineState {
    data object Stopped : EngineState
    data object Starting : EngineState
    data class Running(val nodeId: String?) : EngineState
    data class Error(val message: String) : EngineState
}

/**
 * @param selectionLost a managed node was selected before and has no counterpart now.
 */
data class SubscriptionReplacement(val nodes: List<Node>, val selectionLost: Boolean)

/**
 * @param id upstream server GUID, never a list position.
 * @param delayMs last test result: null when untested, -1 when the test failed.
 */
data class Node(val id: String, val name: String, val protocol: String, val delayMs: Long?)
