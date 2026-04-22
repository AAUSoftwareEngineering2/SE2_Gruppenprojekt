package at.aau.kuhhandel.app.audio

import android.content.Context
import android.media.MediaPlayer

internal fun shouldPlayMenuMusic(isGameStarted: Boolean): Boolean =
    !isGameStarted

internal fun MediaPlayer.managePlayback(isGameStarted: Boolean) {
    if (shouldPlayMenuMusic(isGameStarted)) {
        isLooping = true
        start()
    } else {
        pause()
    }
}

internal fun createMenuMediaPlayer(context: Context): MediaPlayer =
    MediaPlayer.create(context, at.aau.kuhhandel.app.R.raw.mainmenu)

internal fun releaseMediaPlayer(mediaPlayer: MediaPlayer) {
    mediaPlayer.release()
}
