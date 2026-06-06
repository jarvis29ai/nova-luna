package com.nova.luna.music

/**
 * Parser for music-related intents.
 */
class MusicIntentParser {

    fun parse(text: String): MusicRequest {
        val normalizedText = text.lowercase().trim()
        
        // Handle control commands first as they are often short
        val controlRequest = tryParseControlCommand(normalizedText)
        if (controlRequest != null) return controlRequest

        // Handle selections and confirmations
        val selectionRequest = tryParseSelectionOrConfirmation(normalizedText)
        if (selectionRequest != null) return selectionRequest

        // Handle playlist creation
        if (normalizedText.contains("create playlist") || normalizedText.contains("make a playlist")) {
            return parsePlaylistRequest(normalizedText)
        }

        // Handle trending/new songs
        if (normalizedText.contains("new songs") || normalizedText.contains("trending songs") || normalizedText.contains("latest")) {
            return parseTrendingRequest(normalizedText)
        }

        // Default: Parse play requests (song, artist, album, mood, genre, language)
        return parsePlayRequest(normalizedText)
    }

    private fun tryParseControlCommand(text: String): MusicRequest? {
        return when {
            text == "pause" || text == "pause music" -> MusicRequest(commandType = MusicCommandType.PAUSE)
            text == "resume" || text == "resume music" || text == "continue" -> MusicRequest(commandType = MusicCommandType.RESUME)
            text == "next" || text == "next song" || text == "skip" -> MusicRequest(commandType = MusicCommandType.NEXT)
            text == "previous" || text == "previous song" || text == "back" -> MusicRequest(commandType = MusicCommandType.PREVIOUS)
            text == "stop" || text == "stop music" -> MusicRequest(commandType = MusicCommandType.STOP_MUSIC)
            text.contains("increase volume") || text.contains("volume up") || text.contains("louder") -> MusicRequest(commandType = MusicCommandType.INCREASE_VOLUME)
            text.contains("decrease volume") || text.contains("volume down") || text.contains("quieter") -> MusicRequest(commandType = MusicCommandType.DECREASE_VOLUME)
            text.startsWith("set volume to") -> {
                val volume = text.replace("set volume to", "").trim().toIntOrNull()
                MusicRequest(commandType = MusicCommandType.SET_VOLUME, volumeTarget = volume)
            }
            else -> null
        }
    }

    private fun tryParseSelectionOrConfirmation(text: String): MusicRequest? {
        return when {
            text == "yes" || text == "yeah" || text == "sure" || text == "ok" -> MusicRequest(commandType = MusicCommandType.SELECT_CLOSE_MATCH, confirmation = true)
            text == "no" || text == "nope" || text == "don't" -> MusicRequest(commandType = MusicCommandType.SELECT_CLOSE_MATCH, confirmation = false)
            text == "cancel" || text == "stop that" -> MusicRequest(commandType = MusicCommandType.CANCEL)
            text == "first one" || text == "the first one" || text == "1st" -> MusicRequest(commandType = MusicCommandType.SELECT_CLOSE_MATCH, closeMatchSelection = 0)
            text == "second one" || text == "the second one" || text == "2nd" -> MusicRequest(commandType = MusicCommandType.SELECT_CLOSE_MATCH, closeMatchSelection = 1)
            text == "third one" || text == "the third one" || text == "3rd" -> MusicRequest(commandType = MusicCommandType.SELECT_CLOSE_MATCH, closeMatchSelection = 2)
            text == "allow explicit" || text == "play explicit" -> MusicRequest(explicitAllowed = true)
            text == "find clean version" || text == "play clean" -> MusicRequest(explicitAllowed = false)
            else -> null
        }
    }

    private fun parsePlaylistRequest(text: String): MusicRequest {
        var playlistName: String? = null
        val nameMatch = Regex("named (.*)").find(text)
        if (nameMatch != null) {
            playlistName = nameMatch.groupValues[1].trim()
        } else if (text.contains("workout playlist")) {
            playlistName = "workout"
        } else if (text.contains("gym playlist")) {
            playlistName = "gym"
        } else if (text.contains("romantic playlist")) {
            playlistName = "romantic"
        }
        
        return MusicRequest(
            commandType = MusicCommandType.CREATE_PLAYLIST,
            playlistName = playlistName
        )
    }

    private fun parseTrendingRequest(text: String): MusicRequest {
        val language = parseLanguage(text)
        val genre = parseGenre(text)
        return MusicRequest(
            commandType = MusicCommandType.FIND_NEW_SONGS,
            language = language,
            genre = genre
        )
    }

    private fun parsePlayRequest(text: String): MusicRequest {
        var workingText = text.replace("luna ", "").replace("nova ", "").trim()
        if (workingText.startsWith("play")) {
            workingText = workingText.removePrefix("play").trim()
        }
        
        val preferredProvider = parseProvider(workingText)
        val outputPreference = parseOutputDevice(workingText)
        
        // Clean up provider and output names from search text
        workingText = cleanSearchText(workingText)

        val mood = parseMood(workingText)
        val language = parseLanguage(workingText)
        val genre = parseGenre(workingText)
        
        var commandType = MusicCommandType.PLAY_SPECIFIC_SONG
        var songName: String? = null
        var artistName: String? = null
        var albumName: String? = null

        when {
            workingText.contains("songs by") || workingText.contains("song by") -> {
                val parts = workingText.split(Regex("songs? by"))
                songName = if (parts[0].isNotBlank()) parts[0].trim() else null
                artistName = parts[1].trim()
                commandType = if (songName == null) MusicCommandType.PLAY_ARTIST else MusicCommandType.PLAY_SPECIFIC_SONG
            }
            mood != MusicMood.UNKNOWN -> {
                commandType = MusicCommandType.PLAY_MOOD_PLAYLIST
            }
            language != MusicLanguage.UNKNOWN || genre != MusicGenre.UNKNOWN -> {
                commandType = MusicCommandType.PLAY_LANGUAGE_OR_GENRE
            }
            workingText.contains("songs") -> {
                artistName = workingText.replace("songs", "").trim()
                commandType = MusicCommandType.PLAY_ARTIST
            }
            workingText.contains("album") -> {
                albumName = workingText.replace("album", "").trim()
                commandType = MusicCommandType.PLAY_ALBUM
            }
            else -> {
                // Heuristic for song vs artist if not explicitly mentioned
                songName = workingText
            }
        }

        return MusicRequest(
            commandType = commandType,
            songName = songName,
            artistName = artistName,
            albumName = albumName,
            mood = mood,
            language = language,
            genre = genre,
            preferredProvider = preferredProvider,
            outputPreference = outputPreference
        )
    }

    private fun parseProvider(text: String): MusicProvider {
        return when {
            text.contains("spotify") -> MusicProvider.SPOTIFY
            text.contains("youtube music") || text.contains("yt music") -> MusicProvider.YOUTUBE_MUSIC
            text.contains("apple music") -> MusicProvider.APPLE_MUSIC
            text.contains("jiosaavn") || text.contains("saavn") -> MusicProvider.JIOSAAVN
            text.contains("wynk") -> MusicProvider.WYNK_MUSIC
            text.contains("gaana") -> MusicProvider.GAANA
            text.contains("local music") || text.contains("downloaded music") -> MusicProvider.LOCAL_DEVICE_MUSIC
            else -> MusicProvider.UNKNOWN
        }
    }

    private fun parseOutputDevice(text: String): MusicDeviceOutputPreference {
        return when {
            text.contains("bluetooth") -> MusicDeviceOutputPreference.BLUETOOTH
            text.contains("speaker") -> MusicDeviceOutputPreference.PHONE_SPEAKER
            text.contains("earphones") || text.contains("headphones") -> MusicDeviceOutputPreference.EARPHONES
            else -> MusicDeviceOutputPreference.AUTO
        }
    }

    private fun parseMood(text: String): MusicMood {
        return when {
            text.contains("happy") -> MusicMood.HAPPY
            text.contains("sad") -> MusicMood.SAD
            text.contains("romantic") || text.contains("love") -> MusicMood.ROMANTIC
            text.contains("workout") || text.contains("gym") || text.contains("exercise") -> MusicMood.WORKOUT
            text.contains("focus") || text.contains("study") || text.contains("work") -> MusicMood.FOCUS
            text.contains("party") -> MusicMood.PARTY
            text.contains("relax") || text.contains("chill") -> MusicMood.RELAX
            text.contains("devotional") || text.contains("bhakti") -> MusicMood.DEVOTIONAL
            else -> MusicMood.UNKNOWN
        }
    }

    private fun parseLanguage(text: String): MusicLanguage {
        return when {
            text.contains("hindi") -> MusicLanguage.HINDI
            text.contains("english") -> MusicLanguage.ENGLISH
            text.contains("punjabi") -> MusicLanguage.PUNJABI
            text.contains("tamil") -> MusicLanguage.TAMIL
            text.contains("telugu") -> MusicLanguage.TELUGU
            text.contains("marathi") -> MusicLanguage.MARATHI
            text.contains("bengali") -> MusicLanguage.BENGALI
            text.contains("haryanvi") -> MusicLanguage.HARYANVI
            else -> MusicLanguage.UNKNOWN
        }
    }

    private fun parseGenre(text: String): MusicGenre {
        return when {
            text.contains("lo-fi") || text.contains("lofi") -> MusicGenre.LOFI
            text.contains("edm") || text.contains("electronic") -> MusicGenre.EDM
            text.contains("pop") -> MusicGenre.POP
            text.contains("rock") -> MusicGenre.ROCK
            text.contains("hip hop") || text.contains("rap") -> MusicGenre.HIP_HOP
            text.contains("bollywood") -> MusicGenre.BOLLYWOOD
            text.contains("classical") -> MusicGenre.CLASSICAL
            text.contains("devotional") -> MusicGenre.DEVOTIONAL
            else -> MusicGenre.UNKNOWN
        }
    }

    private fun cleanSearchText(text: String): String {
        return text.replace("on spotify", "")
            .replace("on youtube music", "")
            .replace("on apple music", "")
            .replace("on jiosaavn", "")
            .replace("on wynk", "")
            .replace("on gaana", "")
            .replace("on bluetooth", "")
            .replace("on speaker", "")
            .replace("using spotify", "")
            .replace("using youtube music", "")
            .trim()
    }
}
