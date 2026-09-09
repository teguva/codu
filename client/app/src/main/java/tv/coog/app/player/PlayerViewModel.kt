package tv.coog.app.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    val player: ExoPlayer = ExoPlayer.Builder(app).build()
    private var preparedUrl: String? = null

    fun play(url: String, token: String) {
        if (preparedUrl == url && player.mediaItemCount > 0) {
            player.playWhenReady = true
            return
        }
        val http = DefaultHttpDataSource.Factory()
        if (token.isNotBlank()) {
            http.setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
        }
        val source = DefaultMediaSourceFactory(http).createMediaSource(MediaItem.fromUri(url))
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
        preparedUrl = url
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
