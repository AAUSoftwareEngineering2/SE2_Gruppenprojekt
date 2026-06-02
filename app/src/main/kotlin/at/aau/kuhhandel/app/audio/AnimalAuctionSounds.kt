package at.aau.kuhhandel.app.audio

import androidx.compose.runtime.Composable
import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.shared.enums.AnimalType

@Composable
fun rememberAnimalAuctionSound(): (AnimalType) -> Unit {
    val playChicken = rememberMediaSoundEffect(R.raw.chicken)
    val playGoose = rememberMediaSoundEffect(R.raw.goose)
    val playCat = rememberMediaSoundEffect(R.raw.cat)
    val playDog = rememberMediaSoundEffect(R.raw.dog)
    val playSheep = rememberMediaSoundEffect(R.raw.sheep)
    val playGoat = rememberMediaSoundEffect(R.raw.goat)
    val playDonkey = rememberMediaSoundEffect(R.raw.donkey)
    val playPig = rememberMediaSoundEffect(R.raw.pig)
    val playCow = rememberMediaSoundEffect(R.raw.cow)
    val playHorse = rememberMediaSoundEffect(R.raw.horse)

    return { animalType ->
        when (animalType) {
            AnimalType.CHICKEN -> playChicken()
            AnimalType.GOOSE -> playGoose()
            AnimalType.CAT -> playCat()
            AnimalType.DOG -> playDog()
            AnimalType.SHEEP -> playSheep()
            AnimalType.GOAT -> playGoat()
            AnimalType.DONKEY -> playDonkey()
            AnimalType.PIG -> playPig()
            AnimalType.COW -> playCow()
            AnimalType.HORSE -> playHorse()
        }
    }
}
