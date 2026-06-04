package com.nova.luna.brain

interface PhoneBrainProvider : BrainProvider, BrainProviderDiagnostics {
    val available: Boolean
}
