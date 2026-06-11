package com.nova.luna.model

enum class BrainActionType(val wireValue: String) {
    OPEN_APP("open_app"),
    OPEN_CAMERA("open_camera"),
    OPEN_SETTINGS("open_settings"),
    TOGGLE_FLASHLIGHT("toggle_flashlight"),
    SET_DEVICE_SETTING("set_device_setting"),
    SEARCH_WEB("search_web"),
    SEARCH_YOUTUBE("search_youtube"),
    PLAY_MEDIA("play_media"),
    MAKE_CALL_DRAFT("make_call_draft"),
    SEND_MESSAGE_DRAFT("send_message_draft"),
    CREATE_CONTENT("create_content"),
    ASK_QUESTION("ask_question"),
    CAB_SEARCH("cab_search"),
    FOOD_SEARCH("food_search"),
    GROCERY_SEARCH("grocery_search"),
    HUMAN_ONLY("human_only"),
    BOOKING_REQUEST("booking_request"),
    PAYMENT_REQUEST("payment_request"),
    OTP_REQUEST("otp_request"),
    LOGIN_REQUEST("login_request"),
    CAPTCHA_REQUEST("captcha_request"),
    DESTRUCTIVE_REQUEST("destructive_request"),
    PRIVACY_SENSITIVE_REQUEST("privacy_sensitive_request"),
    ASK_CLARIFICATION("ask_clarification"),
    EXTERNAL_ACTION("external_action"),
    NONE("none"),
    UNSUPPORTED("unsupported"),
    UNKNOWN("unknown"),
    
    // Legacy values
    READ_ONLY("read_only"),
    PREPARE("prepare");

    companion object {
        fun fromWireValue(value: String?): BrainActionType? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.wireValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true)
            }
        }
    }
}
