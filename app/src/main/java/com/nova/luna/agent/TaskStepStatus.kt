package com.nova.luna.agent

enum class TaskStepStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    ASK_USER,
    MANUAL_HANDOFF,
    COMPLETE,
    STOPPED
}
