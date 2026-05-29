package at.aau.kuhhandel.app.audio

import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import at.aau.kuhhandel.app.R

val LocalButtonClickSound = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun ButtonClickSoundProvider(content: @Composable () -> Unit) {
    val playButtonClickSound = rememberSoundEffect(R.raw.button_click)

    CompositionLocalProvider(
        LocalButtonClickSound provides playButtonClickSound,
        content = content,
    )
}

@Composable
fun rememberSoundEffect(
    @RawRes soundResId: Int,
): () -> Unit {
    val context = LocalContext.current.applicationContext
    var soundId by remember { mutableStateOf(0) }
    var isLoaded by remember { mutableStateOf(false) }
    val soundPool =
        remember {
            SoundPool
                .Builder()
                .setMaxStreams(4)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                ).build()
        }

    DisposableEffect(soundPool) {
        soundPool.setOnLoadCompleteListener { _, loadedSoundId, status ->
            if (loadedSoundId == soundId && status == 0) {
                isLoaded = true
            }
        }
        soundId = soundPool.load(context, soundResId, 1)

        onDispose {
            soundPool.release()
        }
    }

    return remember(soundPool, soundId, isLoaded) {
        {
            if (isLoaded) {
                soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
            }
        }
    }
}
