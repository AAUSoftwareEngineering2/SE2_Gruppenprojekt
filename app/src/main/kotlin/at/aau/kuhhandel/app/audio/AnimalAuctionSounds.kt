package at.aau.kuhhandel.app.audio

import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.shared.enums.AnimalType

@Composable
fun rememberAnimalAuctionSound(): (AnimalType) -> Unit {
    val context = LocalContext.current.applicationContext
    val soundIds = remember { mutableStateMapOf<AnimalType, Int>() }
    val loadedSounds = remember { mutableStateMapOf<Int, Boolean>() }
    var soundsLoaded by remember { mutableStateOf(false) }
    val soundPool =
        remember {
            SoundPool
                .Builder()
                .setMaxStreams(3)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                ).build()
        }

    DisposableEffect(soundPool) {
        soundPool.setOnLoadCompleteListener { _, loadedSoundId, status ->
            loadedSounds[loadedSoundId] = status == 0
            soundsLoaded =
                soundIds.isNotEmpty() &&
                soundIds.values.all { soundId -> loadedSounds[soundId] == true }
        }

        animalSoundResources.forEach { (animalType, rawResId) ->
            soundIds[animalType] = soundPool.load(context, rawResId, 1)
        }

        onDispose {
            soundPool.release()
        }
    }

    return remember(soundPool, soundIds, soundsLoaded) {
        { animalType ->
            val soundId = soundIds[animalType]
            if (soundId != null && loadedSounds[soundId] == true) {
                soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
            }
        }
    }
}

private val animalSoundResources =
    mapOf(
        AnimalType.CHICKEN to R.raw.chicken,
        AnimalType.GOOSE to R.raw.goose,
        AnimalType.CAT to R.raw.cat,
        AnimalType.DOG to R.raw.dog,
        AnimalType.SHEEP to R.raw.sheep,
        AnimalType.GOAT to R.raw.goat,
        AnimalType.DONKEY to R.raw.donkey,
        AnimalType.PIG to R.raw.pig,
        AnimalType.COW to R.raw.cow,
        AnimalType.HORSE to R.raw.horse,
    )
