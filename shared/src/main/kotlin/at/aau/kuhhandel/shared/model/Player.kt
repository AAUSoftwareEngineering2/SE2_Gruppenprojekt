package at.aau.kuhhandel.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val id: String,
    val name: String,
    val animals: List<AnimalCard> = emptyList(),
    val moneyCards: List<MoneyCard> = emptyList(),
    val isConnected: Boolean = true,
) {
    fun totalMoney(): Int = moneyCards.sumOf { it.value }
}
