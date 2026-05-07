package at.aau.kuhhandel.server.websocket

import at.aau.kuhhandel.server.service.GameService
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.websocket.ErrorPayload
import at.aau.kuhhandel.shared.websocket.GameCreatedPayload
import at.aau.kuhhandel.shared.websocket.GameStatePayload
import at.aau.kuhhandel.shared.websocket.InitiateTradePayload
import at.aau.kuhhandel.shared.websocket.OfferTradePayload
import at.aau.kuhhandel.shared.websocket.RespondToTradePayload
import at.aau.kuhhandel.shared.websocket.WebSocketEnvelope
import at.aau.kuhhandel.shared.websocket.WebSocketJson
import at.aau.kuhhandel.shared.websocket.WebSocketType
import kotlinx.serialization.KSerializer
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

/**
 * Handles game WebSocket messages.
 */
@Component
class GameWebSocketHandler(
    private val gameService: GameService,
    private val connectionRegistry: ConnectionRegistry,
) : TextWebSocketHandler() {
    override fun handleTextMessage(
        session: WebSocketSession,
        message: TextMessage,
    ) {
        val envelope =
            try {
                WebSocketJson.json.decodeFromString(
                    WebSocketEnvelope.serializer(),
                    message.payload,
                )
            } catch (e: Exception) {
                sendError(session, null, "Invalid message format")
                return
            }

        when (envelope.type) {
            WebSocketType.CREATE_GAME -> handleCreateGame(session, envelope)
            WebSocketType.START_GAME -> handleStartGame(session, envelope)
            WebSocketType.REVEAL_CARD -> handleRevealCard(session, envelope)
            WebSocketType.INITIATE_TRADE -> handleInitiateTrade(session, envelope)
            WebSocketType.OFFER_TRADE -> handleOfferTrade(session, envelope)
            WebSocketType.RESPOND_TO_TRADE -> handleRespondToTrade(session, envelope)
            // Server Side TODO: Handle auction message types: PLACE_BID, AUCTION_BUY_BACK
            else -> sendError(session, envelope.requestId, "Unsupported message type")
        }
    }

    override fun afterConnectionClosed(
        session: WebSocketSession,
        status: CloseStatus,
    ) {
        val gameId = connectionRegistry.gameIdFor(session.id)
        if (gameId != null) {
            gameService.removeGame(gameId)
        }
        connectionRegistry.unbind(session.id)
    }

    private fun handleCreateGame(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        // Uses a temporary player ID for now; will be changed when multiplayer is implemented
        val game = gameService.createGame("player-1")
        connectionRegistry.bind(session.id, game.gameId)

        send(
            session,
            WebSocketEnvelope(
                type = WebSocketType.GAME_CREATED,
                requestId = envelope.requestId,
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        GameCreatedPayload.serializer(),
                        GameCreatedPayload(gameId = game.gameId, state = game.gameState),
                    ),
            ),
        )
    }

    private fun handleStartGame(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val gameId =
            connectionRegistry.gameIdFor(session.id)
                ?: return sendError(session, envelope.requestId, ERROR_NO_GAME_BOUND)

        val state =
            gameService.startGame(gameId)
                ?: return sendError(session, envelope.requestId, ERROR_GAME_NOT_FOUND)

        send(
            session,
            WebSocketEnvelope(
                type = WebSocketType.GAME_STARTED,
                requestId = envelope.requestId,
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        GameStatePayload.serializer(),
                        GameStatePayload(state),
                    ),
            ),
        )
    }

    private fun handleRevealCard(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val gameId =
            connectionRegistry.gameIdFor(session.id)
                ?: return sendError(session, envelope.requestId, ERROR_NO_GAME_BOUND)

        val state =
            gameService.revealNextCard(gameId)
                ?: return sendError(session, envelope.requestId, ERROR_GAME_NOT_FOUND)

        send(
            session,
            WebSocketEnvelope(
                type = WebSocketType.GAME_STATE_UPDATED,
                requestId = envelope.requestId,
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        GameStatePayload.serializer(),
                        GameStatePayload(state),
                    ),
            ),
        )
    }

    private fun handleInitiateTrade(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val gameId =
            connectionRegistry.gameIdFor(session.id)
                ?: return sendError(session, envelope.requestId, ERROR_NO_GAME_BOUND)

        val payload =
            decodePayload(session, envelope, InitiateTradePayload.serializer())
                ?: return

        val state =
            try {
                gameService.chooseTrade(gameId, payload.challengedPlayerId)
            } catch (e: IllegalArgumentException) {
                return sendError(session, envelope.requestId, e.message ?: "Invalid trade request")
            } catch (e: IllegalStateException) {
                return sendError(
                    session,
                    envelope.requestId,
                    e.message ?: ERROR_INVALID_TRADE_STATE,
                )
            }

        if (state == null) {
            return sendError(session, envelope.requestId, ERROR_GAME_NOT_FOUND)
        }

        sendStateUpdate(session, envelope.requestId, state)
    }

    private fun handleOfferTrade(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val gameId =
            connectionRegistry.gameIdFor(session.id)
                ?: return sendError(session, envelope.requestId, ERROR_NO_GAME_BOUND)

        val payload =
            decodePayload(session, envelope, OfferTradePayload.serializer())
                ?: return

        val state =
            try {
                gameService.offerTrade(gameId, payload.moneyCardIds)
            } catch (e: IllegalArgumentException) {
                return sendError(session, envelope.requestId, e.message ?: "Invalid trade offer")
            } catch (e: IllegalStateException) {
                return sendError(
                    session,
                    envelope.requestId,
                    e.message ?: ERROR_INVALID_TRADE_STATE,
                )
            }

        if (state == null) {
            return sendError(session, envelope.requestId, ERROR_GAME_NOT_FOUND)
        }

        sendStateUpdate(session, envelope.requestId, state)
    }

    private fun handleRespondToTrade(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val gameId =
            connectionRegistry.gameIdFor(session.id)
                ?: return sendError(session, envelope.requestId, ERROR_NO_GAME_BOUND)

        val payload =
            decodePayload(session, envelope, RespondToTradePayload.serializer())
                ?: return

        val state =
            try {
                gameService.respondToTrade(gameId, payload.respondingPlayerId, payload.accepted)
            } catch (e: IllegalArgumentException) {
                return sendError(session, envelope.requestId, e.message ?: "Invalid trade response")
            } catch (e: IllegalStateException) {
                return sendError(
                    session,
                    envelope.requestId,
                    e.message ?: ERROR_INVALID_TRADE_STATE,
                )
            }

        if (state == null) {
            return sendError(session, envelope.requestId, ERROR_GAME_NOT_FOUND)
        }

        sendStateUpdate(session, envelope.requestId, state)
    }

    private fun <T> decodePayload(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
        deserializer: KSerializer<T>,
    ): T? {
        val payloadJson = envelope.payload
        if (payloadJson == null) {
            sendError(session, envelope.requestId, "Missing payload for ${envelope.type}")
            return null
        }
        return try {
            WebSocketJson.json.decodeFromJsonElement(deserializer, payloadJson)
        } catch (e: Exception) {
            sendError(session, envelope.requestId, "Invalid payload for ${envelope.type}")
            null
        }
    }

    private fun sendStateUpdate(
        session: WebSocketSession,
        requestId: String?,
        state: GameState,
    ) {
        send(
            session,
            WebSocketEnvelope(
                type = WebSocketType.GAME_STATE_UPDATED,
                requestId = requestId,
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        GameStatePayload.serializer(),
                        GameStatePayload(state),
                    ),
            ),
        )
    }

    private fun send(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val json = WebSocketJson.json.encodeToString(WebSocketEnvelope.serializer(), envelope)
        session.sendMessage(TextMessage(json))
    }

    private fun sendError(
        session: WebSocketSession,
        requestId: String?,
        message: String,
    ) {
        val envelope =
            WebSocketEnvelope(
                type = WebSocketType.ERROR,
                requestId = requestId,
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        ErrorPayload.serializer(),
                        ErrorPayload(message),
                    ),
            )
        send(session, envelope)
    }

    companion object {
        private const val ERROR_NO_GAME_BOUND = "No game bound to this connection"
        private const val ERROR_GAME_NOT_FOUND = "Game not found"
        private const val ERROR_INVALID_TRADE_STATE = "Invalid trade state"
    }
}
