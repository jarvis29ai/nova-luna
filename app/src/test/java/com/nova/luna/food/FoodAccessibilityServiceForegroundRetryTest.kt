package com.nova.luna.food

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FoodAccessibilityServiceForegroundRetryTest {
    @Test
    fun `awaitProviderForeground ignores the Nova screen and waits for the provider screen`() {
        val service = object : FoodAccessibilityService() {
            private val snapshots = listOf(
                snapshot("com.nova.luna"),
                snapshot("in.swiggy.android")
            )
            private var index = 0

            override fun captureScreenSnapshot(): FoodCartSnapshot? {
                return snapshots.getOrNull(index++) ?: snapshots.last()
            }
        }

        assertTrue(service.awaitProviderForeground("in.swiggy.android", retries = 2, pollDelayMs = 0))
    }

    private fun snapshot(packageName: String): FoodCartSnapshot {
        return FoodCartSnapshot(
            visibleText = listOf("sample"),
            screenPackageName = packageName,
            manualActionReason = null
        )
    }
}
