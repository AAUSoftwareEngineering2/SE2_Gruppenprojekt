package at.aau.kuhhandel.shared.websocket

import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.GameStateView
import kotlinx.serialization.Serializable

/**
 * Payload used by CREATE_GAME commands
 */
@Serializable
data class CreateGamePayload(
    val playerName: String? = null,
)

/**
 * Payload used by GAME_CREATED events
 */
@Serializable
data class GameCreatedPayload(
    val gameId: String,
    val playerId: String,
    val state: GameState,
    val stateView: GameStateView? = null,
)

/**
 * Payload used by GAME_STATE_UPDATED and SNAPSHOT events
 */
@Serializable
data class GameStatePayload(
    val state: GameState,
    val stateView: GameStateView? = null,
)

/**
 * Payload used by JOIN_GAME commands
 */
@Serializable
data class JoinGamePayload(
    val gameId: String,
    val playerName: String? = null,
)

/**
 * Payload used by GAME_JOINED events
 */
@Serializable
data class GameJoinedPayload(
    val playerId: String,
    val state: GameState,
    val stateView: GameStateView? = null,
)

/**
 * Payload used by RECONNECT commands
 */
@Serializable
data class ReconnectPayload(
    val gameId: String,
    val playerId: String,
)

/**
 * Payload used by ERROR events
 */
@Serializable
data class ErrorPayload(
    val message: String,
)

/**
 * Payload used by INITIATE_TRADE commands.
 * Sent by the active (initiating) player to start a trade challenge against another player.
 */
@Serializable
data class InitiateTradePayload(
    val challengedPlayerId: String,
    val animalType: AnimalType,
    val moneyCardIds: Set<String>,
)

/**
 * Payload used by RESPOND_TO_TRADE commands.
 * Sent by the challenged player to accept or reject the pending trade offer.
 */
@Serializable
data class RespondToTradePayload(
    val respondingPlayerId: String,
    val counterOfferedMoneyCardIds: Set<String> = emptySet(),
)

/**
 * Payload used by PLACE_BID commands.
 */
@Serializable
data class PlaceBidPayload(
    val amount: Int,
)

/**
 * Payload used by AUCTION_BUY_BACK commands.
 */
@Serializable
data class AuctionBuyBackPayload(
    val buyBack: Boolean,
)
