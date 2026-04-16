package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.AnimalType

data class TradeState(
    // Player who started the trade
    val initiatingPlayerId: String,
    // Player who was challenged
    val challengedPlayerId: String,
    // Animal type that is requested in the trade
    val requestedAnimalType: AnimalType,
    // Initial money offer
    val offeredMoney: Int = 0,
    // Optional counter offer from the challenged player
    val counterOfferedMoney: Int? = null,
    // Can later be used to mark the trade as finished
    val isResolved: Boolean = false,
)
