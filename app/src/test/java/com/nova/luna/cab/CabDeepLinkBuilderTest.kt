package com.nova.luna.cab

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CabDeepLinkBuilderTest {
    private lateinit var packageManager: PackageManager
    private lateinit var context: TestContext
    private lateinit var registry: CabProviderRegistry
    private lateinit var builder: CabDeepLinkBuilder

    @Before
    fun setUp() {
        packageManager = Mockito.mock(PackageManager::class.java)
        context = TestContext(ApplicationProvider.getApplicationContext(), packageManager)
        registry = CabProviderRegistry(packageManager)
        builder = CabDeepLinkBuilder(context, registry)
    }

    @Test
    fun `uber launch intent uses deep link and embeds trip extras without launching any app`() {
        val request = CabBookingRequest(
            rawText = "book auto to DB Mall",
            pickupLocation = "Home",
            dropLocation = "DB Mall",
            rideType = RideType.AUTO,
            preferredProvider = CabProvider.UBER
        )
        val selectedOption = CabFareOption(
            provider = CabProvider.UBER,
            rideType = RideType.AUTO,
            visibleFareText = "₹124",
            visibleFareAmount = 124L,
            finalFareText = "₹124",
            finalFareAmount = 124L,
            etaText = "ETA 5 min",
            etaMinutes = 5,
            packageName = CabProviderRegistry.UBER_PACKAGE_NAME
        )

        val intent = builder.buildLaunchIntent(CabProvider.UBER, request, selectedOption)

        assertNotNull(intent)
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals(CabProviderRegistry.UBER_PACKAGE_NAME, intent?.`package`)
        assertTrue(intent?.dataString?.startsWith("uber://?action=setPickup") == true)
        assertTrue(intent?.dataString?.contains("pickup=Home") == true)
        assertTrue(intent?.dataString?.contains("dropoff%5Bformatted_address%5D=DB%20Mall") == true)
        assertTrue(intent?.flags?.and(Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        assertEquals(CabProvider.UBER.name, intent?.getStringExtra(CabDeepLinkBuilder.EXTRA_PROVIDER))
        assertEquals("book auto to DB Mall", intent?.getStringExtra(CabDeepLinkBuilder.EXTRA_REQUEST_TEXT))
        assertEquals("Home", intent?.getStringExtra(CabDeepLinkBuilder.EXTRA_PICKUP_LOCATION))
        assertEquals("DB Mall", intent?.getStringExtra(CabDeepLinkBuilder.EXTRA_DROP_LOCATION))
        assertEquals(RideType.AUTO.name, intent?.getStringExtra(CabDeepLinkBuilder.EXTRA_RIDE_TYPE))
        assertEquals(CabProvider.UBER.name, intent?.getStringExtra(CabDeepLinkBuilder.EXTRA_SELECTED_PROVIDER))
        assertEquals(RideType.AUTO.name, intent?.getStringExtra(CabDeepLinkBuilder.EXTRA_SELECTED_RIDE_TYPE))
        assertEquals("₹124", intent?.getStringExtra(CabDeepLinkBuilder.EXTRA_VISIBLE_FARE_TEXT))
        assertEquals("₹124", intent?.getStringExtra(CabDeepLinkBuilder.EXTRA_FINAL_FARE_TEXT))
        assertEquals(124L, intent?.getLongExtra(CabDeepLinkBuilder.EXTRA_VISIBLE_FARE_AMOUNT, -1L))
        assertEquals(124L, intent?.getLongExtra(CabDeepLinkBuilder.EXTRA_FINAL_FARE_AMOUNT, -1L))
        assertEquals("ETA 5 min", intent?.getStringExtra(CabDeepLinkBuilder.EXTRA_ETA_TEXT))
        assertEquals(5, intent?.getIntExtra(CabDeepLinkBuilder.EXTRA_ETA_MINUTES, -1))
        Mockito.verifyNoInteractions(packageManager)
    }

    @Test
    fun `fallback provider launch intent reuses package manager intent and adds trip extras`() {
        val launchIntent = Intent(Intent.ACTION_MAIN).setPackage(CabProviderRegistry.OLA_PACKAGE_NAME)
        Mockito.`when`(packageManager.getLaunchIntentForPackage(CabProviderRegistry.OLA_PACKAGE_NAME))
            .thenReturn(launchIntent)

        val request = CabBookingRequest(
            rawText = "book cab to DB Mall",
            pickupLocation = "Home",
            dropLocation = "DB Mall",
            rideType = RideType.MINI,
            preferredProvider = CabProvider.OLA
        )

        val intent = builder.buildLaunchIntent(CabProvider.OLA, request, null)

        assertNotNull(intent)
        assertSame(launchIntent, intent)
        assertEquals(Intent.ACTION_MAIN, intent?.action)
        assertEquals(CabProviderRegistry.OLA_PACKAGE_NAME, intent?.`package`)
        assertTrue(intent?.flags?.and(Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        assertEquals(CabProvider.OLA.name, intent?.getStringExtra(CabDeepLinkBuilder.EXTRA_PROVIDER))
        assertEquals("book cab to DB Mall", intent?.getStringExtra(CabDeepLinkBuilder.EXTRA_REQUEST_TEXT))
        assertEquals("Home", intent?.getStringExtra(CabDeepLinkBuilder.EXTRA_PICKUP_LOCATION))
        assertEquals("DB Mall", intent?.getStringExtra(CabDeepLinkBuilder.EXTRA_DROP_LOCATION))
        assertEquals(RideType.MINI.name, intent?.getStringExtra(CabDeepLinkBuilder.EXTRA_RIDE_TYPE))
        assertEquals(CabProvider.OLA.name, intent?.getStringExtra(CabDeepLinkBuilder.EXTRA_PREFERRED_PROVIDER))
        Mockito.verify(packageManager, Mockito.atLeastOnce())
            .getLaunchIntentForPackage(CabProviderRegistry.OLA_PACKAGE_NAME)
    }

    private class TestContext(
        baseContext: Context,
        private val packageManager: PackageManager
    ) : ContextWrapper(baseContext) {
        override fun getApplicationContext(): Context = this
        override fun getPackageManager(): PackageManager = packageManager
    }
}
