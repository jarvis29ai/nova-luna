package com.nova.luna.brain

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType

class MediaHandler : DomainHandler {
    override val domain: UnifiedDomain = UnifiedDomain.MEDIA
    override val modelName: String = "MediaParser"

    override fun canHandle(command: String, context: AssistantContext?): DomainMatch {
        val signals = mutableListOf<String>()
        val mediaKeywords = listOf(
            "youtube", "instagram", "reels", "shorts", "netflix", "hotstar", "prime video", "movie", "video", "show"
        )

        for (keyword in mediaKeywords) {
            if (command.contains(keyword)) {
                signals.add(keyword)
            }
        }

        val confidence = when {
            signals.contains("youtube") && (command.contains("play") || command.contains("search")) -> 0.98f
            signals.contains("youtube") || signals.contains("netflix") -> 0.85f // Lower than SystemHandler's 0.95 for simple open
            signals.isNotEmpty() -> 0.92f
            else -> 0.0f
        }
        
        return DomainMatch(
            domain = domain,
            confidence = confidence,
            matchedSignals = signals,
            reason = "Media keywords found"
        )
    }

    override fun parse(command: String, context: AssistantContext?): CommandIntent {
        return CommandIntent(
            rawText = command,
            normalizedText = com.nova.luna.util.AssistantTextNormalizer.normalize(command),
            intentType = IntentType.MEDIA_CONTROL,
            actionType = ActionType.MEDIA_CONTROL
        )
    }
}

class MusicHandler : DomainHandler {
    override val domain: UnifiedDomain = UnifiedDomain.MUSIC
    override val modelName: String = "MusicParser"

    private val playbackSignals = setOf("play", "pause", "resume", "skip", "next", "previous", "stop")

    override fun canHandle(command: String, context: AssistantContext?): DomainMatch {
        val signals = mutableListOf<String>()
        val musicKeywords = listOf(
            "song", "artist", "album", "playlist", "music", "spotify", "yt music", "jiosaavn", "wynk", "gaana"
        )

        for (keyword in musicKeywords) {
            if (command.contains(keyword)) {
                signals.add(keyword)
            }
        }

        val hasPlayback = playbackSignals.any { command.contains(it) }
        if (hasPlayback) {
            signals.add("playback")
        }

        val isBarePlayback = playbackSignals.contains(command)

        val confidence = when {
            signals.size >= 2 && !isBarePlayback -> 0.95f
            signals.contains("music") || signals.contains("spotify") || signals.contains("yt music") -> 0.90f
            signals.isNotEmpty() && !isBarePlayback -> 0.85f
            isBarePlayback -> 0.40f // Ambiguous unless session active
            else -> 0.0f
        }
        
        return DomainMatch(
            domain = domain,
            confidence = confidence,
            matchedSignals = signals,
            reason = if (signals.isNotEmpty()) "Music signals found" else "No music signals"
        )
    }

    override fun parse(command: String, context: AssistantContext?): CommandIntent {
        val entities = mutableMapOf<String, String>()
        com.nova.luna.memory.MemoryContextUtil.getPreference(context?.memoryContext, "music")?.let {
            entities["preferredApp"] = it
        }
        
        return CommandIntent(
            rawText = command,
            normalizedText = com.nova.luna.util.AssistantTextNormalizer.normalize(command),
            intentType = IntentType.CONTROL,
            actionType = ActionType.MUSIC,
            entities = entities
        )
    }
}
