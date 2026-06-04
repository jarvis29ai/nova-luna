package com.nova.luna.brain

interface BrainProvider {
    fun analyze(request: BrainRequest): String
}
