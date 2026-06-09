package com.nova.luna.modelinstall

class DeviceCapabilityChecker(
    private val selector: ModelPackSelector = ModelPackSelector()
) {
    fun select(snapshot: DeviceCapabilitySnapshot): ModelPackSelection {
        return selector.select(snapshot)
    }

    fun recommendedPack(snapshot: DeviceCapabilitySnapshot): ModelPackSelection {
        return select(snapshot)
    }

    fun recommendedPackId(snapshot: DeviceCapabilitySnapshot): ModelPackId {
        return select(snapshot).packId
    }
}
