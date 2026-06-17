package at.aau.kuhhandel.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Opponent(
    val id: String,
    val name: String,
    val animals: List<AnimalCard>,
    val moneyCardCount: Int,
    val isConnected: Boolean,
)
