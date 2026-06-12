package com.nova.luna.screen

enum class RecoveryStrategy {
    WAIT_AND_RESNAPSHOT,
    SCROLL_AND_RETRY,
    GO_BACK_AND_RETRY,
    OPEN_APP_AGAIN,
    USE_ALTERNATIVE_ELEMENT,
    ASK_USER_MANUAL_STEP,
    FAIL_SAFE
}
