package at.aau.kuhhandel.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import at.aau.kuhhandel.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val NORMAL_GAME_MUSIC_SPEED = 1.0f
private const val AUCTION_GAME_MUSIC_SPEED = 1.2f
private const val GAME_MUSIC_START_COMPLETION_BUFFER_MS = 2_500L
private const val GAME_MUSIC_LOOP_VOLUME = 0.35f
private const val DUCKED_GAME_MUSIC_LOOP_VOLUME = 0.12f

val LocalGameMusicDucking = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

@Composable
fun GameMusicPlayer(
    isGameStarted: Boolean,
    isAuctionActive: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val startPlayer = remember { createGameStartMediaPlayer(context) }
    val loopPlayer = remember { createGameLoopMediaPlayer(context) }
    var duckingRequestCount by remember { mutableIntStateOf(0) }
    val setDucking: (Boolean) -> Unit =
        remember {
            { isDucking ->
                duckingRequestCount =
                    (duckingRequestCount + if (isDucking) 1 else -1).coerceAtLeast(0)
            }
        }

    DisposableEffect(startPlayer, loopPlayer) {
        onDispose {
            releaseMediaPlayer(startPlayer)
            releaseMediaPlayer(loopPlayer)
        }
    }

    LaunchedEffect(duckingRequestCount) {
        loopPlayer.setLoopVolume(
            if (duckingRequestCount > 0) {
                DUCKED_GAME_MUSIC_LOOP_VOLUME
            } else {
                GAME_MUSIC_LOOP_VOLUME
            },
        )
    }

    LaunchedEffect(isGameStarted) {
        if (isGameStarted) {
            loopPlayer.pause()
            loopPlayer.seekTo(0)
            startPlayer.seekTo(0)
            startPlayer.start()
            startPlayer.awaitCompletion()
            delay(GAME_MUSIC_START_COMPLETION_BUFFER_MS)
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

    CompositionLocalProvider(LocalGameMusicDucking provides setDucking) {
        content()
    }
}

private fun createGameStartMediaPlayer(context: Context): MediaPlayer =
    MediaPlayer.create(
        context,
        R.raw.game_start,
        gameMusicAudioAttributes,
        0,
    )

private fun createGameLoopMediaPlayer(context: Context): MediaPlayer =
    MediaPlayer
        .create(
            context,
            R.raw.music_game_loop,
            gameMusicAudioAttributes,
            0,
        ).apply {
            isLooping = true
            setVolume(GAME_MUSIC_LOOP_VOLUME, GAME_MUSIC_LOOP_VOLUME)
        }

private fun MediaPlayer.setPlaybackSpeed(speed: Float) {
    playbackParams = playbackParams.setSpeed(speed)
}

private fun MediaPlayer.setLoopVolume(volume: Float) {
    setVolume(volume, volume)
}

private suspend fun MediaPlayer.awaitCompletion() {
    suspendCancellableCoroutine { continuation ->
        setOnCompletionListener { completedPlayer ->
            completedPlayer.setOnCompletionListener(null)
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
        }
        continuation.invokeOnCancellation {
            setOnCompletionListener(null)
        }
    }
}

private val gameMusicAudioAttributes: AudioAttributes =
    AudioAttributes
        .Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
