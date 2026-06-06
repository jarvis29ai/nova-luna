package com.nova.luna.music

/**
 * Builds the mini music card for the Luna/Nova popup.
 */
class MusicMiniCardBuilder {

    fun build(session: MusicSession): MusicMiniCard? {
        val result = session.selectedResult ?: return null
        
        return MusicMiniCard(
            songName = result.songName,
            artist = result.artist,
            albumOrPlaylist = result.album ?: result.playlist,
            appUsed = result.provider.name,
            playbackStatus = session.playbackState,
            hasLyrics = false, // Placeholder
            explicitWarning = result.isExplicit
        )
    }
}
