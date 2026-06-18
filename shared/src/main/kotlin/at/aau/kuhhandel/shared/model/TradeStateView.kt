package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.AnimalType
import kotlinx.serialization.Serializable

@Serializable
data class TradeStateView(
    val initiatorId: String,
    val targetId: String,
    val animalCards: List<AnimalCard>,
    val initiatorCardCount: Int?,
    val targetCardCount: Int?,
    val requestedAnimalType: AnimalType? = null,
    val visibleInitiatorCards: List<MoneyCard>? = null,
    val visibleTargetCards: List<MoneyCard>? = null,
    val winnerId: String? = null,
)
