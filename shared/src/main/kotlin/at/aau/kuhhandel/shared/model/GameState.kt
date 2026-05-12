package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.GamePhase
import kotlinx.serialization.Serializable

@Serializable
data class GameState(
    val phase: GamePhase = GamePhase.NOT_STARTED,
    val roundNumber: Int = 0,
    val deck: AnimalDeck = AnimalDeck(),
    val currentFaceUpCard: AnimalCard? = null,
    val currentPlayerIndex: Int = 0,
    val players: List<PlayerState> = emptyList(),
    val hostPlayerId: String? = null,
    // Active auction state, null if no auction is running
    val auctionState: AuctionState? = null,
    // Active trade state, null if no trade is running
    val tradeState: TradeState? = null,
)
