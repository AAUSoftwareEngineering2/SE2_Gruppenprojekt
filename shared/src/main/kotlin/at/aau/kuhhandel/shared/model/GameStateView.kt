package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.GamePhase
import kotlinx.serialization.Serializable

@Serializable
data class GameStateView(
    val phase: GamePhase,
    val timerEnd: Long?,
    val localPlayer: Player,
    val opponents: List<Opponent>,
    val hostPlayerId: String,
    val roundNumber: Int,
    val currentPlayerIndex: Int,
    val deckSize: Int,
    val auctionState: AuctionState?,
    val tradeState: TradeStateView?,
    val lastEvent: GameEvent?,
)
