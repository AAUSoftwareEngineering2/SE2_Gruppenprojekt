package at.aau.kuhhandel.app.audio

import androidx.compose.runtime.Composable
import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.shared.enums.AnimalType

@Composable
fun rememberAnimalAuctionSound(): (AnimalType) -> Unit {
    val playChicken = rememberMediaSoundEffect(R.raw.animal_chicken)
    val playGoose = rememberMediaSoundEffect(R.raw.animal_goose)
    val playCat = rememberMediaSoundEffect(R.raw.animal_cat)
    val playDog = rememberMediaSoundEffect(R.raw.animal_dog)
    val playSheep = rememberMediaSoundEffect(R.raw.animal_sheep)
    val playGoat = rememberMediaSoundEffect(R.raw.animal_goat)
    val playMoneyAfterDonkeyReveal = rememberMediaSoundEffect(R.raw.trade_donkey_money)
    val playPig = rememberMediaSoundEffect(R.raw.animal_pig)
    val playCow = rememberMediaSoundEffect(R.raw.animal_cow)
    val playHorse = rememberMediaSoundEffect(R.raw.animal_horse)

    return { animalType ->
        when (animalType) {
            AnimalType.CHICKEN -> playChicken()
            AnimalType.GOOSE -> playGoose()
            AnimalType.CAT -> playCat()
            AnimalType.DOG -> playDog()
            AnimalType.SHEEP -> playSheep()
            AnimalType.GOAT -> playGoat()
            AnimalType.DONKEY -> playMoneyAfterDonkeyReveal()
            AnimalType.PIG -> playPig()
            AnimalType.COW -> playCow()
            AnimalType.HORSE -> playHorse()
        }
    }
}
