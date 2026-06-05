package com.nova.luna.model

enum class InternetPermissionCategory(val wireValue: String) {
    LOCAL_ONLY("local_only"),
    INTERNET_OPTIONAL("internet_optional"),
    INTERNET_REQUIRED_FOR_INFO("internet_required_for_info"),
    BLOCKED_SENSITIVE("blocked_sensitive");

    companion object {
        fun fromWireValue(value: String?): InternetPermissionCategory? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.wireValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value, ignoreCase = true)
            }
        }
    }
}
