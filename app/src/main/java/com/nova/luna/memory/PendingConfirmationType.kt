package com.nova.luna.memory

enum class PendingConfirmationType(val wireValue: String) {
    SEND_MESSAGE("send_message"),
    SEND_EMAIL("send_email"),
    PLACE_ORDER("place_order"),
    BOOK_RIDE("book_ride"),
    ADD_TO_CART("add_to_cart"),
    FOLLOW("follow"),
    SUBSCRIBE("subscribe"),
    POST("post"),
    SHARE("share"),
    CALL_CONTACT("call_contact"),
    SAVE_CONTACT("save_contact"),
    OPEN_PAYMENT_PAGE("open_payment_page"),
    EXPORT_CONTENT("export_content"),
    ONLINE_AI_USE("online_ai_use"),
    TYPE_TEXT("type_text"),
    TAP_TARGET("tap_target"),
    GENERIC_SAFE_ACTION("generic_safe_action"),
    MANUAL_HANDOFF("manual_handoff");

    companion object {
        fun fromWireValue(value: String?): PendingConfirmationType? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.wireValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true)
            }
        }
    }
}
