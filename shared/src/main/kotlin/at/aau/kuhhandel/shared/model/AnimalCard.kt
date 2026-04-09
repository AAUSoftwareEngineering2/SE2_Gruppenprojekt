package at.aau.kuhhandel.shared.model
import at.aau.kuhhandel.shared.enums.AnimalType

data class AnimalCard (
    val id: String, // id because there are multiple cards of the same animal
    val type: AnimalType,

    )
