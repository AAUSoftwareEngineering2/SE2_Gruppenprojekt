package at.aau.kuhhandel.app.ui.components

import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.shared.enums.AnimalType

/** Maps an [AnimalType] to its corresponding drawable resource ID. */
fun getAnimalDrawable(type: AnimalType): Int =
    when (type) {
        AnimalType.CHICKEN -> R.drawable.hs_chicken
        AnimalType.GOOSE -> R.drawable.hs_goose
        AnimalType.CAT -> R.drawable.hs_cat
        AnimalType.DOG -> R.drawable.hs_dog
        AnimalType.SHEEP -> R.drawable.hs_sheep
        AnimalType.GOAT -> R.drawable.hs_goat
        AnimalType.DONKEY -> R.drawable.hs_donkey
        AnimalType.PIG -> R.drawable.hs_pig
        AnimalType.COW -> R.drawable.hs_cow
        AnimalType.HORSE -> R.drawable.hs_horse
    }
