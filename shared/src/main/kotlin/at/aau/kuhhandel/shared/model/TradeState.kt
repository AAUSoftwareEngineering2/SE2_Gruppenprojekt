package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.AnimalType
import kotlinx.serialization.Serializable

@Serializable
data class TradeState(
    // Player who started the trade
    val initiatorId: String,
    // Player who was challenged
    val targetId: String,
    // Animal type that is requested in the trade
    val requestedAnimalType: AnimalType,
    // New field to replace requestedAnimalType
    val animalCards: Set<AnimalCard> = emptySet(),
    // Initial money offer value, derived from the selected money cards.
    val offeredMoney: Int = 0,
    // Money cards selected by the initiating player.
    val offeredMoneyCardIds: Set<String> = emptySet(),
    // Optional counter offer value from the challenged player.
    val counterOfferedMoney: Int? = null,
    // Money cards selected by the challenged player for the counter offer.
    val counterOfferedMoneyCardIds: Set<String> = emptySet(),
    // Can later be used to mark the trade as finished
    val isResolved: Boolean = false,
    // New fields to replace offeredMoney, offeredMoneyCardIds,
    // counterOfferedMoney, and counterOfferedMoneyCardIds
    val offeredMoneyCards: Set<MoneyCard>? = null,
    val counterOfferedMoneyCards: Set<MoneyCard>? = null,
    // The ID of the player who received the traded cards in the end
    val winnerId: String? = null,
)
