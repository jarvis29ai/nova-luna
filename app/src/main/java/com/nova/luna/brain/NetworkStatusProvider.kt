package com.nova.luna.brain

interface NetworkStatusProvider {
    fun isInternetAvailable(): Boolean
    fun isMetered(): Boolean = false
    fun isRoaming(): Boolean = false
}

data class StaticNetworkStatusProvider(
    private val internetAvailable: Boolean,
    private val metered: Boolean = false,
    private val roaming: Boolean = false
) : NetworkStatusProvider {
    override fun isInternetAvailable(): Boolean = internetAvailable
    override fun isMetered(): Boolean = metered
    override fun isRoaming(): Boolean = roaming
}
