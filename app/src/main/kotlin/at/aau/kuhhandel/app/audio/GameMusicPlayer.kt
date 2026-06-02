package at.aau.kuhhandel.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import at.aau.kuhhandel.app.R
import kotlinx.coroutines.delay

private const val NORMAL_GAME_MUSIC_SPEED = 1.0f
private const val AUCTION_GAME_MUSIC_SPEED = 1.2f
private const val GAME_MUSIC_START_DELAY_MS = 2_000L

@Composable
fun GameMusicPlayer(
    isGameStarted: Boolean,
    isAuctionActive: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val startPlayer = remember { createGameStartMediaPlayer(context) }
    val loopPlayer = remember { createGameLoopMediaPlayer(context) }

    DisposableEffect(startPlayer, loopPlayer) {
        onDispose {
            releaseMediaPlayer(startPlayer)
            releaseMediaPlayer(loopPlayer)
        }
    }

    LaunchedEffect(isGameStarted) {
        if (isGameStarted) {
            loopPlayer.pause()
            loopPlayer.seekTo(0)
            startPlayer.seekTo(0)
            startPlayer.start()
            delay(GAME_MUSIC_START_DELAY_MS)
            if (!loopPlayer.isPlaying) {
                loopPlayer.start()
            }
        } else {
            startPlayer.pause()
            startPlayer.seekTo(0)
            loopPlayer.pause()
            loopPlayer.seekTo(0)
        }
    }

    LaunchedEffect(isAuctionActive) {
        loopPlayer.setPlaybackSpeed(
            if (isAuctionActive) {
                AUCTION_GAME_MUSIC_SPEED
            } else {
                NORMAL_GAME_MUSIC_SPEED
            },
        )
    }

    content()
}

private fun createGameStartMediaPlayer(context: Context): MediaPlayer =
    MediaPlayer.create(
        context,
        R.raw.game_start_sound,
        gameMusicAudioAttributes,
        0,
    )

private fun createGameLoopMediaPlayer(context: Context): MediaPlayer =
    MediaPlayer
        .create(
            context,
            R.raw.mainmenu_loop,
            gameMusicAudioAttributes,
            0,
        ).apply {
            isLooping = true
        }

private fun MediaPlayer.setPlaybackSpeed(speed: Float) {
    playbackParams = playbackParams.setSpeed(speed)
}

private val gameMusicAudioAttributes: AudioAttributes =
    AudioAttributes
        .Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
