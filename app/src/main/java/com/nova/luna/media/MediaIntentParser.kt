package com.nova.luna.media

class MediaIntentParser {
    fun parse(text: String): MediaRequest {
        val lowerText = text.lowercase()
        
        val provider = detectProvider(lowerText)
        val appType = detectAppType(provider, lowerText)
        val commandType = detectCommandType(lowerText)
        
        return MediaRequest(
            command = text,
            provider = provider,
            appType = appType,
            commandType = commandType,
            searchQuery = extractSearchQuery(lowerText, commandType),
            contentTitle = extractContentTitle(lowerText),
            creatorName = extractCreatorName(lowerText),
            selectionIndex = extractSelectionIndex(lowerText),
            selectionTitle = extractSelectionTitle(lowerText),
            scrollDirection = detectScrollDirection(lowerText),
            scrollSpeed = detectScrollSpeed(lowerText),
            socialAction = detectSocialAction(lowerText),
            commentText = extractCommentText(lowerText),
            ottAction = detectOttAction(lowerText),
            settingAction = detectSettingAction(lowerText),
            settingValue = extractSettingValue(lowerText, detectSettingAction(lowerText)),
            playbackControl = detectPlaybackControl(lowerText),
            isConfirmation = detectConfirmation(lowerText)
        )
    }

    private fun detectProvider(text: String): MediaProvider {
        return when {
            text.contains("youtube shorts") -> MediaProvider.YOUTUBE_SHORTS
            text.contains("youtube") -> MediaProvider.YOUTUBE
            text.contains("reels") -> MediaProvider.INSTAGRAM_REELS
            text.contains("instagram") -> MediaProvider.INSTAGRAM
            text.contains("netflix") -> MediaProvider.NETFLIX
            text.contains("jiohotstar") || text.contains("hotstar") -> MediaProvider.JIOHOTSTAR
            text.contains("prime video") || text.contains("amazon prime") -> MediaProvider.PRIME_VIDEO
            else -> MediaProvider.UNKNOWN_APP
        }
    }

    private fun detectAppType(provider: MediaProvider, text: String): MediaAppType {
        if (provider != MediaProvider.UNKNOWN_APP) {
            return when (provider) {
                MediaProvider.YOUTUBE, MediaProvider.YOUTUBE_SHORTS -> MediaAppType.VIDEO
                MediaProvider.INSTAGRAM, MediaProvider.INSTAGRAM_REELS -> MediaAppType.SOCIAL
                MediaProvider.NETFLIX, MediaProvider.JIOHOTSTAR, MediaProvider.PRIME_VIDEO -> MediaAppType.OTT
                else -> MediaAppType.UNKNOWN
            }
        }
        return when {
            text.contains("video") || text.contains("play") -> MediaAppType.VIDEO
            text.contains("social") || text.contains("feed") -> MediaAppType.SOCIAL
            text.contains("movie") || text.contains("show") || text.contains("episode") || text.contains("watch") -> MediaAppType.OTT
            else -> MediaAppType.UNKNOWN
        }
    }

    private fun detectCommandType(text: String): MediaCommandType {
        return when {
            text.contains("open watchlist") || text.contains("open my list") || text.contains("my list on") || (text.contains("my") && text.contains("watchlist")) -> MediaCommandType.OPEN_WATCHLIST
            text.contains("open") && (text.contains("youtube") || text.contains("instagram") || text.contains("netflix") || text.contains("hotstar") || text.contains("prime video")) -> MediaCommandType.OPEN_APP
            text.contains("search") || text.contains("find") || text.contains("play") && (text.contains("video") || text.contains("song") || text.contains("movie")) -> MediaCommandType.SEARCH_CONTENT
            text.contains("scroll") || text.contains("next reel") || text.contains("previous reel") || text.contains("next short") || text.contains("previous short") -> MediaCommandType.SCROLL_FEED
            text.contains("select") || text.contains("open first") || text.contains("open second") || text.contains("play the") -> MediaCommandType.SELECT_VISIBLE_ITEM
            text.contains("pause") -> MediaCommandType.PAUSE
            text.contains("resume") -> MediaCommandType.RESUME
            text.contains("forward") -> MediaCommandType.FORWARD
            text.contains("back") -> MediaCommandType.BACKWARD
            text.contains("full screen") -> MediaCommandType.FULL_SCREEN
            text.contains("exit full screen") -> MediaCommandType.EXIT_FULL_SCREEN
            text.contains("next video") || text.contains("next episode") -> MediaCommandType.NEXT_CONTENT
            text.contains("previous video") || text.contains("previous episode") -> MediaCommandType.PREVIOUS_CONTENT
            text.contains("like") -> MediaCommandType.LIKE
            text.contains("save") -> MediaCommandType.SAVE
            text.contains("subscribe") -> MediaCommandType.SUBSCRIBE
            text.contains("follow") -> MediaCommandType.FOLLOW
            text.contains("comment") -> MediaCommandType.COMMENT
            text.contains("open channel") || text.contains("open profile") || text.contains("open creator") -> MediaCommandType.OPEN_PROFILE
            text.contains("add to watchlist") || text.contains("add to my list") -> MediaCommandType.ADD_TO_WATCHLIST
            text.contains("remove from watchlist") || text.contains("remove from my list") -> MediaCommandType.REMOVE_FROM_WATCHLIST
            text.contains("download") -> MediaCommandType.DOWNLOAD_CONTENT
            text.contains("open watchlist") || text.contains("open my list") || text.contains("my list on") || (text.contains("my") && text.contains("watchlist")) -> MediaCommandType.OPEN_WATCHLIST
            text.contains("quality") -> MediaCommandType.CHANGE_QUALITY
            text.contains("subtitle") -> MediaCommandType.TOGGLE_SUBTITLES
            text.contains("audio") -> MediaCommandType.CHANGE_AUDIO_LANGUAGE
            text.contains("speed") -> MediaCommandType.CHANGE_SPEED
            text.contains("stop") || text.contains("exit") || text.contains("close") -> MediaCommandType.STOP_EXIT
            text.contains("cancel") -> MediaCommandType.CANCEL
            text.startsWith("play") -> MediaCommandType.PLAY
            else -> MediaCommandType.UNKNOWN
        }
    }

    private fun extractSearchQuery(text: String, commandType: MediaCommandType): String? {
        if (commandType != MediaCommandType.SEARCH_CONTENT && !text.startsWith("play")) return null
        
        val markers = listOf("search for", "search", "find", "play", "open")
        var query = text
        for (marker in markers) {
            if (query.contains(marker)) {
                query = query.substringAfter(marker).trim()
                break
            }
        }
        
        // Remove app names from query
        val appNames = listOf("on youtube", "in youtube", "on instagram", "in instagram", "on netflix", "in netflix", "on hotstar", "in hotstar", "on prime video", "in prime video")
        for (appName in appNames) {
            query = query.replace(appName, "").trim()
        }
        
        return query.takeIf { it.isNotBlank() }
    }

    private fun extractContentTitle(text: String): String? {
        // Simple heuristic for now
        return null
    }

    private fun extractCreatorName(text: String): String? {
        if (text.contains("by ")) return text.substringAfter("by ").trim()
        if (text.contains("from ")) return text.substringAfter("from ").trim()
        return null
    }

    private fun extractSelectionIndex(text: String): Int? {
        return when {
            text.contains("first") || text.contains("1st") -> 0
            text.contains("second") || text.contains("2nd") -> 1
            text.contains("third") || text.contains("3rd") -> 2
            text.contains("fourth") || text.contains("4th") -> 3
            text.contains("fifth") || text.contains("5th") -> 4
            else -> null
        }
    }

    private fun extractSelectionTitle(text: String): String? {
        if (text.contains("select ")) return text.substringAfter("select ").trim()
        if (text.contains("play ")) return text.substringAfter("play ").trim()
        return null
    }

    private fun detectScrollDirection(text: String): MediaScrollDirection {
        return when {
            text.contains("down") || text.contains("next") -> MediaScrollDirection.DOWN
            text.contains("up") || text.contains("previous") -> MediaScrollDirection.UP
            else -> MediaScrollDirection.NONE
        }
    }

    private fun detectScrollSpeed(text: String): String? {
        return when {
            text.contains("slow") -> "slow"
            text.contains("fast") -> "fast"
            else -> "normal"
        }
    }

    private fun detectSocialAction(text: String): MediaSocialAction {
        return when {
            text.contains("like") -> MediaSocialAction.LIKE
            text.contains("save") -> MediaSocialAction.SAVE
            text.contains("subscribe") -> MediaSocialAction.SUBSCRIBE
            text.contains("follow") -> MediaSocialAction.FOLLOW
            text.contains("comment") -> MediaSocialAction.COMMENT
            else -> MediaSocialAction.NONE
        }
    }

    private fun extractCommentText(text: String): String? {
        if (text.contains("comment ")) return text.substringAfter("comment ").trim()
        return null
    }

    private fun detectOttAction(text: String): MediaOttAction {
        if (text.contains("my watchlist")) return MediaOttAction.OPEN_WATCHLIST
        return when {
            text.contains("add to watchlist") || text.contains("add to my list") -> MediaOttAction.ADD_TO_WATCHLIST
            text.contains("remove from watchlist") || text.contains("remove from my list") -> MediaOttAction.REMOVE_FROM_WATCHLIST
            text.contains("download") -> MediaOttAction.DOWNLOAD
            text.contains("open watchlist") || text.contains("open my list") || text.contains("my list on") -> MediaOttAction.OPEN_WATCHLIST
            else -> MediaOttAction.NONE
        }
    }

    private fun detectSettingAction(text: String): MediaSettingAction {
        return when {
            text.contains("quality") -> MediaSettingAction.QUALITY
            text.contains("subtitle") -> MediaSettingAction.SUBTITLES
            text.contains("audio") -> MediaSettingAction.AUDIO_LANGUAGE
            text.contains("speed") -> MediaSettingAction.SPEED
            else -> MediaSettingAction.NONE
        }
    }

    private fun extractSettingValue(text: String, action: MediaSettingAction): String? {
        return when (action) {
            MediaSettingAction.QUALITY -> {
                val match = Regex("(\\d+p)").find(text)
                match?.value ?: text.substringAfter("quality to").trim()
            }
            MediaSettingAction.SUBTITLES -> {
                if (text.contains("on")) "on" else if (text.contains("off")) "off" else null
            }
            MediaSettingAction.AUDIO_LANGUAGE -> text.substringAfter("audio to").trim()
            MediaSettingAction.SPEED -> {
                val match = Regex("(\\d+(\\.\\d+)?x)").find(text)
                match?.value ?: text.substringAfter("speed to").trim()
            }
            MediaSettingAction.NONE -> null
        }
    }

    private fun detectPlaybackControl(text: String): MediaPlaybackControl {
        return when {
            text.contains("pause") -> MediaPlaybackControl.PAUSE
            text.contains("resume") -> MediaPlaybackControl.RESUME
            text.contains("forward") -> MediaPlaybackControl.FORWARD
            text.contains("back") -> MediaPlaybackControl.BACKWARD
            text.contains("full screen") && !text.contains("exit") -> MediaPlaybackControl.FULL_SCREEN
            text.contains("exit full screen") -> MediaPlaybackControl.EXIT_FULL_SCREEN
            text.contains("next") -> MediaPlaybackControl.NEXT
            text.contains("previous") -> MediaPlaybackControl.PREVIOUS
            text.startsWith("play") || text == "play" -> MediaPlaybackControl.PLAY
            else -> MediaPlaybackControl.NONE
        }
    }

    private fun detectConfirmation(text: String): Boolean? {
        return when {
            text == "yes" || text == "yeah" || text == "sure" || text == "do it" || text == "confirm" -> true
            text == "no" || text == "nope" || text == "cancel" || text == "stop" -> false
            else -> null
        }
    }
}
