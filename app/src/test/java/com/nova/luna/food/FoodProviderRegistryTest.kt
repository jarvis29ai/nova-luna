package com.nova.luna.food

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FoodProviderRegistryTest {
    private lateinit var packageManager: PackageManager
    private lateinit var registry: FoodProviderRegistry

    @Before
    fun setUp() {
        packageManager = Mockito.mock(PackageManager::class.java)
        registry = FoodProviderRegistry(packageManager)
    }

    @Test
    fun `installed providers are detected and Toings is skipped when missing`() {
        installProvider(FoodProvider.SWIGGY)
        installProvider(FoodProvider.ZOMATO)

        val installed = registry.installedProviders()
        val missing = registry.missingProviders()

        assertEquals(listOf(FoodProvider.SWIGGY, FoodProvider.ZOMATO), installed)
        assertTrue(missing.contains(FoodProvider.TOINGS))
        assertEquals("in.swiggy.android", registry.packageName(FoodProvider.SWIGGY))
    }

    @Test
    fun `toings is discovered from the launcher label when the package name differs`() {
        Mockito.`when`(packageManager.queryIntentActivities(Mockito.any(Intent::class.java), Mockito.anyInt()))
            .thenReturn(
                listOf(
                    ResolveInfo().apply {
                        nonLocalizedLabel = "Toings Delivery"
                        activityInfo = ActivityInfo().apply {
                            packageName = "com.toings.fresh"
                            name = "MainActivity"
                            applicationInfo = ApplicationInfo().apply {
                                this.packageName = "com.toings.fresh"
                            }
                        }
                    }
                )
            )

        val launchIntent = registry.resolveLaunchIntent(FoodProvider.TOINGS)

        assertNotNull(launchIntent)
        assertEquals("com.toings.fresh", launchIntent?.component?.packageName)
    }

    private fun installProvider(provider: FoodProvider) {
        Mockito.`when`(packageManager.getLaunchIntentForPackage(registry.packageName(provider)))
            .thenReturn(Intent(Intent.ACTION_MAIN))
    }
}
