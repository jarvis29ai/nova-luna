package com.nova.luna.screen

data class ScreenTreeSnapshot(
    val nodes: List<ScreenNode>,
    val rawNodeCount: Int,
    val truncated: Boolean
)
