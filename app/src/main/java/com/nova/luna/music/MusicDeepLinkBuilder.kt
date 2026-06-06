package com.nova.luna.music

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Builds deep links for various music providers.
 */
class MusicDeepLinkBuilder(private val context: Context) {

    fun buildSearchIntent(provider: MusicProvider, query: String): Intent? {
        return when (provider) {
            MusicProvider.SPOTIFY -> buildSpotifySearch(query)
            MusicProvider.YOUTUBE_MUSIC -> buildYouTubeMusicSearch(query)
            MusicProvider.APPLE_MUSIC -> buildAppleMusicSearch(query)
            MusicProvider.JIOSAAVN -> buildJioSaavnSearch(query)
            else -> buildGenericSearch(provider, query)
        }
    }

    private fun buildSpotifySearch(query: String): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("spotify:search:$query")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    private fun buildYouTubeMusicSearch(query: String): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("https://music.youtube.com/search?q=$query")
        intent.setPackage("com.google.android.apps.youtube.music")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    private fun buildAppleMusicSearch(query: String): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("https://music.apple.com/search?term=$query")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    private fun buildJioSaavnSearch(query: String): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("saavn://search/$query")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    private fun buildGenericSearch(provider: MusicProvider, query: String): Intent {
        val intent = Intent(Intent.ACTION_SEARCH)
        intent.putExtra("query", query)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }
}
