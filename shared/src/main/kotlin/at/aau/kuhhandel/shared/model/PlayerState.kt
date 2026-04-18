package at.aau.kuhhandel.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayerState(
    val id: String,
    val name: String,
    val animals: List<AnimalCard> = emptyList(),
    val moneyCards: List<MoneyCard> = emptyList(),
) {
    fun totalMoney(): Int = moneyCards.sumOf { it.value }
}
