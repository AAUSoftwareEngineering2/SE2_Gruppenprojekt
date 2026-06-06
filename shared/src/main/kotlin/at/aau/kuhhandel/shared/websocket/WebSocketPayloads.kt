package at.aau.kuhhandel.shared.websocket

import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.GameStateView
import kotlinx.serialization.Serializable

/**
 * Payload used by [WebSocketType.CREATE_GAME] commands
 */
@Serializable
data class CreateGamePayload(
    val playerName: String? = null,
)

/**
 * Payload used by [WebSocketType.GAME_CREATED] events
 */
@Serializable
data class GameCreatedPayload(
    val gameId: String,
    val playerId: String,
    val reconnectToken: String,
    val state: GameState,
    val stateView: GameStateView? = null,
)

/**
 * Payload used by [WebSocketType.GAME_STATE_UPDATED] events
 */
@Serializable
data class GameStatePayload(
    val state: GameState,
    val stateView: GameStateView? = null,
)

/**
 * Payload used by [WebSocketType.JOIN_GAME] commands
 */
@Serializable
data class JoinGamePayload(
    val gameId: String,
    val playerName: String? = null,
)

/**
 * Payload used by [WebSocketType.GAME_JOINED] events
 */
@Serializable
data class GameJoinedPayload(
    val playerId: String,
    val reconnectToken: String,
    val state: GameState,
    val stateView: GameStateView? = null,
)

/**
 * Payload used by [WebSocketType.RECONNECT] commands
 */
@Serializable
data class ReconnectPayload(
    val gameId: String,
    val playerId: String,
    val token: String,
)

/**
 * Payload used by [WebSocketType.SNAPSHOT] events
 */
@Serializable
data class SnapshotPayload(
    val reconnectToken: String,
    val state: GameState,
    val stateView: GameStateView? = null,
)

/**
 * Payload used by [WebSocketType.ERROR] events
 */
@Serializable
data class ErrorPayload(
    val message: String,
)

/**
 * Payload used by [WebSocketType.CHOOSE_TRADE] commands.
 * Sent by the active player to start a trade challenge against another player.
 */
@Serializable
data class ChooseTradePayload(
    val challengedPlayerId: String,
    val animalType: AnimalType,
    val moneyCardIds: Set<String>,
)

/**
 * Payload used by [WebSocketType.SUBMIT_TRADE_MONEY] commands.
 * Sent by the trade initiator to submit a money offer.
 */
@Serializable
data class SubmitTradeMoneyPayload(
    val moneyCardIds: Set<String>,
)

/**
 * Payload used by [WebSocketType.RESPOND_TO_TRADE] commands.
 * Sent by the challenged player to accept or reject the pending trade offer.
 */
@Serializable
data class RespondToTradePayload(
    val respondingPlayerId: String,
    val counterOfferedMoneyCardIds: Set<String> = emptySet(),
)

/**
 * Payload used by [WebSocketType.PLACE_BID] commands.
 */
@Serializable
data class PlaceBidPayload(
    val amount: Int,
)

/**
 * Payload used by [WebSocketType.RESOLVE_AUCTION] commands.
 */
@Serializable
data class AuctionBuyBackPayload(
    val buyBack: Boolean,
)
