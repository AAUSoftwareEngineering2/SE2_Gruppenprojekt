package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.TradeStep
import kotlinx.serialization.Serializable

@Serializable
data class TradeState(
    // Player who started the trade
    val initiatingPlayerId: String,
    // Player who was challenged
    val challengedPlayerId: String,
    // Animal type that is requested in the trade
    val requestedAnimalType: AnimalType,
    // Current server-side step of the trade interaction.
    val step: TradeStep = TradeStep.WAITING_FOR_RESPONSE,
    // Initial money offer value, derived from the selected money cards.
    val offeredMoney: Int = 0,
    // Money cards selected by the initiating player.
    val offeredMoneyCardIds: List<String> = emptyList(),
    // Publicly safe information for hidden offers.
    val offeredMoneyCardCount: Int = offeredMoneyCardIds.size,
    // Optional counter offer value from the challenged player.
    val counterOfferedMoney: Int? = null,
    // Money cards selected by the challenged player for the counter offer.
    val counterOfferedMoneyCardIds: List<String> = emptyList(),
    // Publicly safe information for hidden counter offers.
    val counterOfferedMoneyCardCount: Int = counterOfferedMoneyCardIds.size,
    // Can later be used to mark the trade as finished
    val isResolved: Boolean = false,
)
