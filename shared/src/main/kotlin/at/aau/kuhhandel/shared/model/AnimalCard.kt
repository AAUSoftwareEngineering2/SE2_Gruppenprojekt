package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.AnimalType

data class AnimalCard(
    val id: String,
    val type: AnimalType,
)
