package com.nova.luna.music

import android.content.Context
import android.media.AudioManager

/**
 * Controls music playback via MediaSession or safe Android APIs.
 */
class MusicPlaybackController(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun pause() {
        // Implementation would use MediaSessionManager or sending media button intents
    }

    fun resume() {
        // Implementation would use MediaSessionManager or sending media button intents
    }

    fun next() {
        // Implementation
    }

    fun previous() {
        // Implementation
    }

    fun stop() {
        // Implementation
    }

    fun adjustVolume(increase: Boolean) {
        val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    fun setVolume(percent: Int) {
        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 100
        val targetVolume = (maxVolume * (percent / 100.0)).toInt()
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, AudioManager.FLAG_SHOW_UI)
    }
}
