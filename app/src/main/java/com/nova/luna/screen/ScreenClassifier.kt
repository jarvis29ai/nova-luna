package com.nova.luna.screen

interface ScreenClassifier {
    fun classify(snapshot: ScreenSnapshot): ScreenSnapshot
}
