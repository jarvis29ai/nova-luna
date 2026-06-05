package com.nova.luna.brain

import com.nova.luna.model.InternetPermissionCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class InternetPermissionPolicyTest {
    private val policy = InternetPermissionPolicy()

    @Test
    fun `safe assistant prompts stay local only`() {
        val decision = policy.classify("Luna what can you do?")

        assertEquals(InternetPermissionCategory.LOCAL_ONLY, decision.category)
    }

    @Test
    fun `live info requests are marked as internet required`() {
        val decision = policy.classify("what is the weather in Delhi?")

        assertEquals(InternetPermissionCategory.INTERNET_REQUIRED_FOR_INFO, decision.category)
    }

    @Test
    fun `cab booking prompts stay local instead of hitting the live info gate`() {
        val decision = policy.classify("Luna book a cab from current location to DB Mall")

        assertEquals(InternetPermissionCategory.LOCAL_ONLY, decision.category)
    }

    @Test
    fun `dangerous payment and final step requests are blocked`() {
        val phrases = listOf(
            "Luna pay 500 rupees to Rahul",
            "Hey Luna, enter this OTP automatically",
            "Nova, book it without asking me",
            "Luna final booking",
            "Hey Nova, delete my files",
            "Luna complete the payment"
        )

        phrases.forEach { phrase ->
            val decision = policy.classify(phrase)
            assertEquals(InternetPermissionCategory.BLOCKED_SENSITIVE, decision.category)
        }
    }
}
