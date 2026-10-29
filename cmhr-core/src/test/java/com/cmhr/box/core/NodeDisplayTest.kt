package com.cmhr.box.core

import org.junit.Assert.*
import org.junit.Test

class NodeDisplayTest {
    @Test fun collidingShortLabelsKeepTheDistinguishingSuffix() {
        val peers = listOf("Hong Kong | A", "Hong Kong | B", "Tokyo | C")
        assertEquals("Hong Kong | A", NodeDisplay.name(peers[0], "Node 1", peers))
        assertEquals("Hong Kong | B", NodeDisplay.name(peers[1], "Node 2", peers))
        assertEquals("Tokyo", NodeDisplay.name(peers[2], "Node 3", peers))
    }

    @Test fun emptyNameUsesFallback() {
        assertEquals("Node 1", NodeDisplay.name("", "Node 1", emptyList()))
    }
}
