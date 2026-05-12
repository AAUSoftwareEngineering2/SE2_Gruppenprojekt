package at.aau.kuhhandel.shared.websocket

import at.aau.kuhhandel.shared.model.GameState
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
)

/**
 * Payload used by GAME_STARTED, GAME_JOINED, and GAME_STATE_UPDATED events
 */
@Serializable
data class GameStatePayload(
    val state: GameState,
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
)

/**
 * Payload used by OFFER_TRADE commands.
 * Sent by the initiating player to attach an offer of one or more of their money cards to the
 * pending trade. Cards are referenced by id and must exist in the initiator's hand.
 */
@Serializable
data class OfferTradePayload(
    val moneyCardIds: List<String>,
)

/**
 * Payload used by RESPOND_TO_TRADE commands.
 * Sent by the challenged player to accept or reject the pending trade offer.
 */
@Serializable
data class RespondToTradePayload(
    val respondingPlayerId: String,
    val accepted: Boolean,
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
