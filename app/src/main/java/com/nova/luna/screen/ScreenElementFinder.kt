package com.nova.luna.screen

interface ScreenElementFinder {
    fun findElement(snapshot: ScreenSnapshot, query: ElementQuery): ElementMatch
}
