package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.utils.GameRankEntry
import kotlinx.serialization.Serializable

@Serializable
data class GameStateView(
    val phase: GamePhase,
    val timerEnd: Long?,
    val localPlayer: Player,
    val opponents: List<Opponent>,
    val hostPlayerId: String,
    val roundNumber: Int,
    val currentPlayerId: String?,
    val deckSize: Int,
    val auctionState: AuctionState?,
    val tradeState: TradeStateView?,
    val alreadySpied: Boolean,
    val spyingTargetId: String?,
    val spyingTargetCards: List<MoneyCard>?,
    val localPlayerSpiedOn: Boolean,
    val spiedOnOpponentIds: List<String>,
    val lastEvent: GameEvent?,
    val finalRanking: List<GameRankEntry>?,
)
