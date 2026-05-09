package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.AnimalType
import kotlinx.serialization.Serializable

@Serializable
data class TradeState(
    // Player who started the trade
    val initiatingPlayerId: String,
    // Player who was challenged
    val challengedPlayerId: String,
    // Animal type that is requested in the trade
    val requestedAnimalType: AnimalType,
    // Initial money offer (sum of values of offeredMoneyCardIds; kept in sync for convenience)
    val offeredMoney: Int = 0,
    // Concrete money cards the initiator has placed on the offer
    val offeredMoneyCardIds: List<String> = emptyList(),
    // Optional counter offer from the challenged player
    val counterOfferedMoney: Int? = null,
    // Can later be used to mark the trade as finished
    val isResolved: Boolean = false,
)
