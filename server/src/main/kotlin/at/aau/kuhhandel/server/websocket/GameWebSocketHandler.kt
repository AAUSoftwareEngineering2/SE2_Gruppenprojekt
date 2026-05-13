package at.aau.kuhhandel.server.websocket

import at.aau.kuhhandel.server.event.GameStateChangedEvent
import at.aau.kuhhandel.server.service.GameService
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.websocket.AuctionBuyBackPayload
import at.aau.kuhhandel.shared.websocket.CreateGamePayload
import at.aau.kuhhandel.shared.websocket.ErrorPayload
import at.aau.kuhhandel.shared.websocket.GameCreatedPayload
import at.aau.kuhhandel.shared.websocket.GameStatePayload
import at.aau.kuhhandel.shared.websocket.InitiateTradePayload
import at.aau.kuhhandel.shared.websocket.JoinGamePayload
import at.aau.kuhhandel.shared.websocket.OfferTradePayload
import at.aau.kuhhandel.shared.websocket.PlaceBidPayload
import at.aau.kuhhandel.shared.websocket.RespondToTradePayload
import at.aau.kuhhandel.shared.websocket.WebSocketEnvelope
import at.aau.kuhhandel.shared.websocket.WebSocketJson
import at.aau.kuhhandel.shared.websocket.WebSocketType
import kotlinx.serialization.KSerializer
import org.springframework.context.event.EventListener
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
    @EventListener
    fun handleGameStateChanged(event: GameStateChangedEvent) {
        val sessions = connectionRegistry.sessionsFor(event.gameId)
        val envelope =
            WebSocketEnvelope(
                type = WebSocketType.GAME_STATE_UPDATED,
                requestId = event.requestId,
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        GameStatePayload.serializer(),
                        GameStatePayload(event.newState),
                    ),
            )
        val json = WebSocketJson.json.encodeToString(WebSocketEnvelope.serializer(), envelope)
        val message = TextMessage(json)

        sessions.forEach { session ->
            if (session.isOpen) {
                session.sendMessage(message)
            }
        }
    }

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
            } catch (_: Exception) {
                sendError(session, null, "Invalid message format")
                return
            }

        when (envelope.type) {
            WebSocketType.CREATE_GAME -> handleCreateGame(session, envelope)
            WebSocketType.START_GAME -> handleStartGame(session, envelope)
            WebSocketType.JOIN_GAME -> handleJoinGame(session, envelope)
            WebSocketType.LEAVE_GAME -> handleLeaveGame(session, envelope)
            WebSocketType.REVEAL_CARD -> handleRevealCard(session, envelope)
            WebSocketType.INITIATE_TRADE -> handleInitiateTrade(session, envelope)
            WebSocketType.OFFER_TRADE -> handleOfferTrade(session, envelope)
            WebSocketType.RESPOND_TO_TRADE -> handleRespondToTrade(session, envelope)
            WebSocketType.PLACE_BID -> handlePlaceBid(session, envelope)
            WebSocketType.AUCTION_BUY_BACK -> handleAuctionBuyBack(session, envelope)
            else -> sendError(session, envelope.requestId, "Unsupported message type")
        }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        connectionRegistry.bindSession(session)
    }

    override fun afterConnectionClosed(
        session: WebSocketSession,
        status: CloseStatus,
    ) {
        connectionRegistry.unbind(session.id)
    }

    private fun handleCreateGame(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        if (connectionRegistry.gameIdFor(session.id) != null) {
            return sendError(
                session,
                envelope.requestId,
                ERROR_GAME_ALREADY_BOUND,
            )
        }

        val payload =
            if (envelope.payload != null) {
                decodePayload(session, envelope, CreateGamePayload.serializer()) ?: return
            } else {
                null
            }
        val playerName = payload?.playerName ?: "Player ${session.id.takeLast(4)}"

        val result = gameService.createGame(playerName)
        val gameId = result.gameId
        val playerId = result.playerId

        connectionRegistry.bindGame(session.id, gameId)
        connectionRegistry.bindPlayer(session.id, playerId)

        send(
            session,
            WebSocketEnvelope(
                type = WebSocketType.GAME_CREATED,
                requestId = envelope.requestId,
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        GameCreatedPayload.serializer(),
                        GameCreatedPayload(
                            gameId = gameId,
                            playerId = playerId,
                            state = result.gameState,
                        ),
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

    private fun handleJoinGame(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        if (connectionRegistry.gameIdFor(session.id) != null) {
            return sendError(
                session,
                envelope.requestId,
                ERROR_GAME_ALREADY_BOUND,
            )
        }

        val payload =
            decodePayload(session, envelope, JoinGamePayload.serializer())
                ?: return

        val gameId = payload.gameId
        // For now, uses a temporary player name if no name is provided; will be changed in the future
        val playerName = payload.playerName ?: "Player ${session.id.takeLast(4)}"

        val result =
            gameService.joinGame(gameId, playerName) ?: return sendError(
                session,
                envelope.requestId,
                ERROR_GAME_NOT_FOUND,
            )

        val joinedGameId = result.gameId
        val playerId = result.playerId

        connectionRegistry.bindGame(session.id, joinedGameId)
        connectionRegistry.bindPlayer(session.id, playerId)

        send(
            session,
            WebSocketEnvelope(
                type = WebSocketType.GAME_JOINED,
                requestId = envelope.requestId,
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        GameCreatedPayload.serializer(),
                        GameCreatedPayload(
                            gameId = joinedGameId,
                            playerId = playerId,
                            state = result.gameState,
                        ),
                    ),
            ),
        )
    }

    private fun handleLeaveGame(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val gameId =
            connectionRegistry.gameIdFor(session.id) ?: return sendError(
                session,
                envelope.requestId,
                ERROR_NO_GAME_BOUND,
            )

        val playerId =
            connectionRegistry.playerIdFor(session.id) ?: return sendError(
                session,
                envelope.requestId,
                ERROR_NO_PLAYER_BOUND,
            )

        gameService.leaveGame(gameId, playerId)
        connectionRegistry.unbind(session.id)

        send(
            session,
            WebSocketEnvelope(
                type = WebSocketType.GAME_LEFT,
                requestId = envelope.requestId,
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
                gameService.chooseTrade(
                    gameId,
                    payload.challengedPlayerId,
                    payload.animalType,
                    payload.moneyCardIds,
                )
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
                gameService.respondToTrade(
                    gameId,
                    payload.respondingPlayerId,
                    payload.accepted,
                    payload.counterOfferedMoneyCardIds, // re-check this!
                )
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

    private fun handlePlaceBid(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val gameId =
            connectionRegistry.gameIdFor(session.id)
                ?: return sendError(session, envelope.requestId, ERROR_NO_GAME_BOUND)

        val payload =
            decodePayload(session, envelope, PlaceBidPayload.serializer())
                ?: return

        val playerId =
            connectionRegistry.playerIdFor(session.id) ?: return sendError(
                session,
                envelope.requestId,
                ERROR_NO_PLAYER_BOUND,
            ) // re-check this!

        val state =
            try {
                gameService.placeBid(gameId, playerId, payload.amount) // re-check this!
            } catch (e: Exception) {
                return sendError(session, envelope.requestId, e.message ?: "Invalid bid")
            }

        if (state == null) {
            return sendError(session, envelope.requestId, ERROR_GAME_NOT_FOUND)
        }

        sendStateUpdate(session, envelope.requestId, state)
    }

    private fun handleAuctionBuyBack(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val gameId =
            connectionRegistry.gameIdFor(session.id)
                ?: return sendError(session, envelope.requestId, ERROR_NO_GAME_BOUND)

        val payload =
            decodePayload(session, envelope, AuctionBuyBackPayload.serializer())
                ?: return

        val state =
            try {
                gameService.resolveAuction(gameId, payload.buyBack)
            } catch (e: Exception) {
                return sendError(session, envelope.requestId, e.message ?: "Invalid buy back")
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
        } catch (_: Exception) {
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
        private const val ERROR_GAME_ALREADY_BOUND = "This connection is already bound to a game"
        private const val ERROR_NO_PLAYER_BOUND = "No player bound to this connection"
        private const val ERROR_GAME_NOT_FOUND = "Game not found"
        private const val ERROR_INVALID_TRADE_STATE = "Invalid trade state"
    }
}
