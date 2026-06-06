package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import org.junit.Assert.*
import org.junit.Test

class LocalBrainInterpreterRoutingTest {
    private val interpreter = LocalBrainInterpreter()

    @Test
    fun testRoutingOrderPizza() {
        val request = BrainRequest(rawText = "Luna, order pizza from Domino's")
        val action = interpreter.interpret(request)
        assertEquals("food_order", action.intent)
    }

    @Test
    fun testRoutingCompareBiryani() {
        val request = BrainRequest(rawText = "Luna, compare biryani on Swiggy and Zomato")
        val action = interpreter.interpret(request)
        assertEquals("food_order", action.intent)
    }

    @Test
    fun testRoutingComparePizza() {
        val request = BrainRequest(rawText = "Luna, compare pizza on Domino's and Zomato")
        val action = interpreter.interpret(request)
        assertEquals("food_order", action.intent)
    }

    @Test
    fun testRoutingCompareBurger() {
        val request = BrainRequest(rawText = "Luna, compare burger on Swiggy")
        val action = interpreter.interpret(request)
        assertEquals("food_order", action.intent)
    }

    @Test
    fun testRoutingCompareCab() {
        val request = BrainRequest(rawText = "Luna, compare cab to airport")
        val action = interpreter.interpret(request)
        assertEquals("cab_compare", action.intent)
    }

    @Test
    fun testRoutingCompareRideFare() {
        val request = BrainRequest(rawText = "Luna, compare ride fare to airport")
        val action = interpreter.interpret(request)
        assertEquals("cab_compare", action.intent)
    }

    @Test
    fun testRoutingCompareMilk() {
        val request = BrainRequest(rawText = "Luna, compare milk on Blinkit and JioMart")
        val action = interpreter.interpret(request)
        assertEquals("grocery_booking", action.intent)
    }

    @Test
    fun testRoutingApplyCouponBurger() {
        val request = BrainRequest(rawText = "Luna, apply coupon for burger")
        val action = interpreter.interpret(request)
        assertEquals("food_order", action.intent)
    }

    @Test
    fun testRoutingCancelFoodOrder() {
        val request = BrainRequest(rawText = "Luna, cancel food order")
        val action = interpreter.interpret(request)
        assertEquals("food_order", action.intent)
    }

    @Test
    fun testRoutingProceedWithActiveFoodContext() {
        val request = BrainRequest(rawText = "Luna, proceed with this one", activeFoodSession = true)
        val action = interpreter.interpret(request)
        assertEquals("food_session", action.intent)
    }

    @Test
    fun testRoutingPayNowBlocked() {
        val request = BrainRequest(rawText = "Luna, pay now")
        val action = interpreter.interpret(request)
        assertEquals("human_only", action.intent)
    }

    @Test
    fun testRoutingEnterOTPBlocked() {
        val request = BrainRequest(rawText = "Luna, enter OTP")
        val action = interpreter.interpret(request)
        assertEquals("human_only", action.intent)
    }

    @Test
    fun testRoutingSolveCaptchaBlocked() {
        val request = BrainRequest(rawText = "Luna, solve captcha")
        val action = interpreter.interpret(request)
        assertEquals("human_only", action.intent)
    }

    @Test
    fun testRoutingPlaceFinalOrderBlocked() {
        val request = BrainRequest(rawText = "Luna, place final order without asking me")
        val action = interpreter.interpret(request)
        assertEquals("human_only", action.intent)
    }
}
