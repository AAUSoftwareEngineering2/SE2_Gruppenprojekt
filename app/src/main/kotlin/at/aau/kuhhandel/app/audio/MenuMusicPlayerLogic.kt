package at.aau.kuhhandel.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import at.aau.kuhhandel.app.R

internal fun shouldPlayMenuMusic(isGameStarted: Boolean): Boolean = !isGameStarted

private const val MENU_MUSIC_VOLUME = 0.55f

private var activeMenuMediaPlayer: MediaPlayer? = null

internal fun registerActiveMenuMediaPlayer(mediaPlayer: MediaPlayer) {
    activeMenuMediaPlayer
        ?.takeIf { it !== mediaPlayer }
        ?.let { previousPlayer ->
            if (previousPlayer.isPlaying) {
                previousPlayer.stop()
            }
            previousPlayer.release()
        }
    activeMenuMediaPlayer = mediaPlayer
}

internal fun releaseMenuMediaPlayer(mediaPlayer: MediaPlayer) {
    if (activeMenuMediaPlayer === mediaPlayer) {
        activeMenuMediaPlayer = null
    }
    releaseMediaPlayer(mediaPlayer)
}

internal fun MediaPlayer.managePlayback(isGameStarted: Boolean) {
    if (shouldPlayMenuMusic(isGameStarted)) {
        isLooping = true
        if (!isPlaying) {
            start()
        }
    } else {
        if (isPlaying) {
            pause()
        }
        seekTo(0)
    }
}

internal fun createMenuMediaPlayer(context: Context): MediaPlayer =
    MediaPlayer
        .create(
            context,
            R.raw.music_menu,
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            0,
        ).apply {
            setVolume(MENU_MUSIC_VOLUME, MENU_MUSIC_VOLUME)
        }

internal fun releaseMediaPlayer(mediaPlayer: MediaPlayer) {
    mediaPlayer.release()
}
