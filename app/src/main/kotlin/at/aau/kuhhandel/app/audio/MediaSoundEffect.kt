package at.aau.kuhhandel.app.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberMediaSoundEffect(
    @RawRes soundResId: Int,
    onPlaybackStarted: () -> Unit = {},
    onPlaybackFinished: () -> Unit = {},
): () -> Unit {
    val context = LocalContext.current.applicationContext
    val activePlayers = remember { mutableStateListOf<MediaPlayer>() }
    val currentOnPlaybackStarted by rememberUpdatedState(onPlaybackStarted)
    val currentOnPlaybackFinished by rememberUpdatedState(onPlaybackFinished)

    DisposableEffect(Unit) {
        onDispose {
            activePlayers.forEach { player ->
                player.release()
                currentOnPlaybackFinished()
            }
            activePlayers.clear()
        }
    }

    return remember(context, activePlayers, soundResId) {
        {
            val player =
                MediaPlayer.create(
                    context,
                    soundResId,
                    gameSoundEffectAttributes,
                    0,
                ) ?: return@remember

            activePlayers += player
            player.setOnCompletionListener { completedPlayer ->
                activePlayers -= completedPlayer
                completedPlayer.release()
                currentOnPlaybackFinished()
            }
            player.setOnErrorListener { erroredPlayer, _, _ ->
                activePlayers -= erroredPlayer
                erroredPlayer.release()
                currentOnPlaybackFinished()
                true
            }
            currentOnPlaybackStarted()
            player.start()
        }
    }
}

val gameSoundEffectAttributes: AudioAttributes =
    AudioAttributes
        .Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
