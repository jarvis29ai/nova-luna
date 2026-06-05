package com.nova.luna.cab

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CabProviderRegistryTest {
    private lateinit var packageManager: PackageManager
    private lateinit var registry: CabProviderRegistry

    @Before
    fun setUp() {
        packageManager = Mockito.mock(PackageManager::class.java)
        registry = CabProviderRegistry(packageManager)
    }

    @Test
    fun `installed providers are detected by package name and missing apps are skipped`() {
        Mockito.`when`(packageManager.getLaunchIntentForPackage(CabProviderRegistry.UBER_PACKAGE_NAME))
            .thenReturn(Intent(Intent.ACTION_MAIN))

        val installed = registry.installedProviders()
        val missing = registry.missingProviders()

        assertEquals(listOf(CabProvider.UBER), installed)
        assertTrue(missing.contains(CabProvider.OLA))
        assertTrue(missing.contains(CabProvider.RAPIDO))
        assertTrue(missing.contains(CabProvider.INDRIVE))
        assertEquals(CabProviderRegistry.UBER_PACKAGE_NAME, registry.packageName(CabProvider.UBER))
    }

    @Test
    fun `inDrive package name falls back across aliases`() {
        Mockito.`when`(packageManager.getLaunchIntentForPackage(CabProviderRegistry.INDRIVE_PACKAGE_NAME_LOWER))
            .thenReturn(Intent(Intent.ACTION_MAIN))

        assertTrue(registry.isInstalled(CabProvider.INDRIVE))
        assertEquals(
            CabProviderRegistry.INDRIVE_PACKAGE_NAME_LOWER,
            registry.installedPackageName(CabProvider.INDRIVE)
        )
    }

    private class TestContext(
        baseContext: Context,
        private val packageManager: PackageManager
    ) : ContextWrapper(baseContext) {
        override fun getApplicationContext(): Context = this
        override fun getPackageManager(): PackageManager = packageManager
    }
}
