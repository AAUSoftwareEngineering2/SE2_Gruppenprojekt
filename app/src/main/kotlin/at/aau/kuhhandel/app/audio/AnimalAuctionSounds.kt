package at.aau.kuhhandel.app.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.shared.enums.AnimalType

@Composable
fun rememberAnimalAuctionSound(): (AnimalType) -> Unit {
    val context = LocalContext.current.applicationContext
    val activePlayers = remember { mutableStateListOf<MediaPlayer>() }

    DisposableEffect(Unit) {
        onDispose {
            activePlayers.forEach { player -> player.release() }
            activePlayers.clear()
        }
    }

    return remember(context, activePlayers) {
        { animalType ->
            val rawResId = animalSoundResources[animalType] ?: return@remember
            val player =
                MediaPlayer.create(
                    context,
                    rawResId,
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                    0,
                ) ?: return@remember

            activePlayers += player
            player.setOnCompletionListener { completedPlayer ->
                activePlayers -= completedPlayer
                completedPlayer.release()
            }
            player.setOnErrorListener { erroredPlayer, _, _ ->
                activePlayers -= erroredPlayer
                erroredPlayer.release()
                true
            }
            player.start()
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
