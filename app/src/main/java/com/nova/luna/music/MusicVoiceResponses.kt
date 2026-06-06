package com.nova.luna.music

/**
 * Generates voice and popup responses for music actions.
 */
class MusicVoiceResponses {

    fun welcome(): MusicResponse {
        return MusicResponse(
            "How can I help you with music today?",
            "How can I help you with music today?",
            MusicStatus.SUCCESS,
            MusicFlowState.IDLE
        )
    }

    fun missingDetails(): MusicResponse {
        return MusicResponse(
            "Which song or artist would you like to hear?",
            "Which song or artist would you like to hear?",
            MusicStatus.NEEDS_USER_INPUT,
            MusicFlowState.ASKING_MISSING_DETAILS
        )
    }

    fun appChoice(apps: List<MusicProviderStatus>): MusicResponse {
        val appNames = apps.joinToString(" and ") { it.displayName }
        return MusicResponse(
            "Which app should I use? I found $appNames.",
            "Which app should I use? I found $appNames.",
            MusicStatus.NEEDS_USER_INPUT,
            MusicFlowState.ASKING_APP_CHOICE
        )
    }

    fun playing(song: String, app: String): MusicResponse {
        return MusicResponse(
            "Playing $song on $app.",
            "Playing $song on $app.",
            MusicStatus.SUCCESS,
            MusicFlowState.STARTING_PLAYBACK
        )
    }

    fun explicitWarning(): MusicResponse {
        return MusicResponse(
            "This song may contain explicit content. Should I play it or find a clean version?",
            "This song may contain explicit content. Should I play it or find a clean version?",
            MusicStatus.NEEDS_CONFIRMATION,
            MusicFlowState.WARNING_EXPLICIT_CONTENT
        )
    }

    fun controlApplied(action: String): MusicResponse {
        return MusicResponse(
            "$action.",
            "$action.",
            MusicStatus.SUCCESS,
            MusicFlowState.COMPLETED
        )
    }

    fun manualActionRequired(reason: String): MusicResponse {
        return MusicResponse(
            "I need you to $reason manually.",
            "I need you to $reason manually.",
            MusicStatus.MANUAL_ACTION_REQUIRED,
            MusicFlowState.MANUAL_ACTION_REQUIRED
        )
    }

    fun playlistCreated(name: String): MusicResponse {
        return MusicResponse(
            "I created the playlist '$name'.",
            "I created the playlist $name.",
            MusicStatus.SUCCESS,
            MusicFlowState.COMPLETED
        )
    }

    fun error(message: String): MusicResponse {
        return MusicResponse(
            message,
            message,
            MusicStatus.FAILED,
            MusicFlowState.FAILED
        )
    }
}
