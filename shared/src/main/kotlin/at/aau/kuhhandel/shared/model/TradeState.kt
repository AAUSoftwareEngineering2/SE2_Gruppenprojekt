package at.aau.kuhhandel.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class TradeState(
    // Player who started the trade
    val initiatorId: String,
    // Player who was challenged
    val targetId: String,
    // Animal cards involved in the trade
    val animalCards: Set<AnimalCard> = emptySet(),
    // Money cards selected by the initiating player (null until the offer is submitted).
    val offeredMoneyCards: Set<MoneyCard>? = null,
    // Money cards selected by the challenged player for the counter offer (null until submitted).
    val counterOfferedMoneyCards: Set<MoneyCard>? = null,
    // The ID of the player who received the traded cards in the end
    val winnerId: String? = null,
)
