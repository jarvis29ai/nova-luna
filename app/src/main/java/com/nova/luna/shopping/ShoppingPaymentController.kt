package com.nova.luna.shopping

class ShoppingPaymentController {
    fun processPayment(method: ShoppingPaymentMethod): ShoppingStatus {
        return when (method) {
            ShoppingPaymentMethod.UPI,
            ShoppingPaymentMethod.CARD,
            ShoppingPaymentMethod.NET_BANKING -> ShoppingStatus.MANUAL_ACTION_REQUIRED
            ShoppingPaymentMethod.COD -> ShoppingStatus.SUCCESS
            ShoppingPaymentMethod.APP_WALLET -> ShoppingStatus.NEEDS_CONFIRMATION
            else -> ShoppingStatus.FAILED
        }
    }
}
