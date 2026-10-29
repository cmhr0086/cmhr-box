package com.cmhr.box.core

/** Compact node labels, ported from 1.x DefaultProxyDisplay. */
object NodeDisplay {
    fun name(rawName: String, fallback: String, peers: List<String>): String {
        val shortName = rawName.substringBefore('|').trim()
        // Different nodes can share the prefix hidden by the compact label.
        val visibleName = if (peers.count { it.substringBefore('|').trim() == shortName } > 1) {
            rawName.trim()
        } else shortName
        return visibleName.ifBlank { rawName.ifBlank { fallback } }
    }
}
