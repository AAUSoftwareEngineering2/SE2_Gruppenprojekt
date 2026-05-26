package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.AnimalType
import kotlinx.serialization.Serializable

@Serializable
data class TradeStateView(
    val initiatorId: String,
    val targetId: String,
    val requestedAnimalType: AnimalType,
    val initiatorCardCount: Int,
    val targetCardCount: Int?,
    val visibleInitiatorCards: List<MoneyCard>? = null,
    val visibleTargetCards: List<MoneyCard>? = null,
)
