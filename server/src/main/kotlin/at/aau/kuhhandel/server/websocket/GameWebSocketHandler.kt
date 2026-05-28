package at.aau.kuhhandel.server.websocket

import at.aau.kuhhandel.server.event.GameStateChangedEvent
import at.aau.kuhhandel.server.exception.GameException
import at.aau.kuhhandel.server.service.GameService
import at.aau.kuhhandel.shared.enums.GameErrorReason
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.websocket.AuctionBuyBackPayload
import at.aau.kuhhandel.shared.websocket.CreateGamePayload
import at.aau.kuhhandel.shared.websocket.ErrorPayload
import at.aau.kuhhandel.shared.websocket.GameCreatedPayload
import at.aau.kuhhandel.shared.websocket.GameJoinedPayload
import at.aau.kuhhandel.shared.websocket.GameStatePayload
import at.aau.kuhhandel.shared.websocket.InitiateTradePayload
import at.aau.kuhhandel.shared.websocket.JoinGamePayload
import at.aau.kuhhandel.shared.websocket.PlaceBidPayload
import at.aau.kuhhandel.shared.websocket.ReconnectPayload
import at.aau.kuhhandel.shared.websocket.RespondToTradePayload
import at.aau.kuhhandel.shared.websocket.WebSocketEnvelope
import at.aau.kuhhandel.shared.websocket.WebSocketJson
import at.aau.kuhhandel.shared.websocket.WebSocketType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import kotlin.coroutines.CoroutineContext

/**
 * Handles game WebSocket messages.
 */
@Component
class GameWebSocketHandler(
    private val gameService: GameService,
    private val connectionRegistry: ConnectionRegistry,
    // Used in tests
    handlerContext: CoroutineContext = Dispatchers.Default + SupervisorJob(),
) : TextWebSocketHandler() {
    private val logger = LoggerFactory.getLogger(GameWebSocketHandler::class.java)
    private val handlerScope = CoroutineScope(handlerContext)

    /**
     * Listens for backend game updates and broadcasts them.
     */
    @EventListener
    fun handleGameStateChanged(event: GameStateChangedEvent) {
        broadcastStateUpdate(event.gameId, event.newState)
    }

    /**
     * Processes incoming network text messages on a background coroutine scope.
     */
    override fun handleTextMessage(
        session: WebSocketSession,
        message: TextMessage,
    ) {
        handlerScope.launch {
            var requestId: String? = null

            try {
                val envelope = decodeEnvelope(message)
                requestId = envelope.requestId

                when (envelope.type) {
                    WebSocketType.CREATE_GAME -> handleCreateGame(session, envelope)
                    WebSocketType.START_GAME -> handleStartGame(session, envelope)
                    WebSocketType.JOIN_GAME -> handleJoinGame(session, envelope)
                    WebSocketType.LEAVE_GAME -> handleLeaveGame(session, envelope)
                    WebSocketType.CHOOSE_AUCTION -> handleChooseAuction(session, envelope)
                    WebSocketType.INITIATE_TRADE -> handleInitiateTrade(session, envelope)
                    WebSocketType.RESPOND_TO_TRADE -> handleRespondToTrade(session, envelope)
                    WebSocketType.PLACE_BID -> handlePlaceBid(session, envelope)
                    WebSocketType.AUCTION_BUY_BACK -> handleAuctionBuyBack(session, envelope)
                    WebSocketType.FINISH_TRADE_REVEAL -> handleFinishTradeReveal(session, envelope)
                    WebSocketType.RECONNECT -> handleReconnect(session, envelope)
                    else -> throw GameException(GameErrorReason.UNSUPPORTED_MESSAGE_TYPE)
                }
            } catch (e: GameException) {
                sendError(session, requestId, e.reason)
            } catch (e: Exception) {
                logger.error("Unexpected error message on session ${session.id}", e)
                sendError(session, requestId, GameErrorReason.INTERNAL_SERVER_ERROR)
            }
        }
    }

    /**
     * Registers a new network connection into the registry.
     */
    override fun afterConnectionEstablished(session: WebSocketSession) {
        connectionRegistry.bindSession(session)
    }

    /**
     * Cleans up or disconnects an existing active socket connection.
     */
    override fun afterConnectionClosed(
        session: WebSocketSession,
        status: CloseStatus,
    ) {
        val gameId = connectionRegistry.gameIdFor(session.id)
        val playerId = connectionRegistry.playerIdFor(session.id)

        if (gameId != null && playerId != null) {
            handlerScope.launch {
                performLeave(session.id, gameId, playerId)
            }
        } else {
            connectionRegistry.unbind(session.id)
        }
    }

    /**
     * Processes [WebSocketType.CREATE_GAME] commands.
     */
    private suspend fun handleCreateGame(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        ensureNoBoundGame(session.id)

        val payload = decodePayload(envelope, CreateGamePayload.serializer())
        // For now, uses a temporary player name if no name is provided; will be changed in the future
        val playerName = payload.playerName ?: "Player ${session.id.takeLast(4)}"

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
                            stateView = result.gameState.createViewForPlayer(playerId),
                        ),
                    ),
            ),
        )
    }

    /**
     * Processes [WebSocketType.START_GAME] commands.
     */
    private suspend fun handleStartGame(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val gameId = requireBoundGame(session.id)
        val actorId = requireBoundPlayer(session.id)

        val state = gameService.startGame(gameId, actorId)

        sendStateUpdate(session, envelope.requestId, state, actorId)
        broadcastStateUpdate(gameId, state, session)
    }

    /**
     * Processes [WebSocketType.JOIN_GAME] commands.
     */
    private suspend fun handleJoinGame(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        ensureNoBoundGame(session.id)
        val payload = decodePayload(envelope, JoinGamePayload.serializer())

        val gameId = payload.gameId
        // For now, uses a temporary player name if no name is provided; will be changed in the future
        val playerName = payload.playerName ?: "Player ${session.id.takeLast(4)}"

        val result = gameService.joinGame(gameId, playerName)

        val joinedGameId = result.gameId
        val playerId = result.playerId
        val state = result.gameState

        connectionRegistry.bindGame(session.id, joinedGameId)
        connectionRegistry.bindPlayer(session.id, playerId)

        send(
            session,
            WebSocketEnvelope(
                type = WebSocketType.GAME_JOINED,
                requestId = envelope.requestId,
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        GameJoinedPayload.serializer(),
                        GameJoinedPayload(
                            playerId = playerId,
                            state = state,
                            stateView = state.createViewForPlayer(playerId),
                        ),
                    ),
            ),
        )

        broadcastStateUpdate(joinedGameId, state, session)
    }

    /**
     * Processes [WebSocketType.LEAVE_GAME] commands.
     */
    private suspend fun handleLeaveGame(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val gameId = requireBoundGame(session.id)
        val playerId = requireBoundPlayer(session.id)

        send(
            session,
            WebSocketEnvelope(
                type = WebSocketType.GAME_LEFT,
                requestId = envelope.requestId,
            ),
        )

        performLeave(session.id, gameId, playerId)
    }

    /**
     * Executes the exit logic in the service and cleans up connection bindings.
     */
    private suspend fun performLeave(
        sessionId: String,
        gameId: String,
        playerId: String,
    ) {
        try {
            val state = gameService.leaveGame(gameId, playerId)
            broadcastStateUpdate(gameId, state)
        } catch (e: Exception) {
            logger.info(
                "Player {} could not leave game {} on disconnect: {}",
                playerId,
                gameId,
                e.message,
            )
        } finally {
            connectionRegistry.unbind(sessionId)
        }
    }

    /**
     * Processes [WebSocketType.RECONNECT] commands.
     */
    private suspend fun handleReconnect(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        ensureNoBoundGame(session.id)
        val payload = decodePayload(envelope, ReconnectPayload.serializer())

        // getGame transparently rehydrates from persistence when no live in-memory session exists
        val gameSession =
            gameService.getGame(payload.gameId)
                ?: throw GameException(GameErrorReason.GAME_NOT_FOUND)

        if (!gameSession.hasPlayer(payload.playerId)) {
            throw GameException(GameErrorReason.PLAYER_NOT_IN_GAME)
        }

        val state = gameSession.state

        connectionRegistry.bindGame(session.id, payload.gameId)
        connectionRegistry.bindPlayer(session.id, payload.playerId)

        send(
            session,
            WebSocketEnvelope(
                type = WebSocketType.SNAPSHOT,
                requestId = envelope.requestId,
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        GameStatePayload.serializer(),
                        GameStatePayload(
                            state = state,
                            stateView = state.createViewForPlayer(payload.playerId),
                        ),
                    ),
            ),
        )
    }

    /**
     * Processes [WebSocketType.CHOOSE_AUCTION] commands.
     */
    private suspend fun handleChooseAuction(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val gameId = requireBoundGame(session.id)
        val actorId = requireBoundPlayer(session.id)

        val state = gameService.chooseAuction(gameId, actorId)

        sendStateUpdate(session, envelope.requestId, state, actorId)
        broadcastStateUpdate(gameId, state, session)
    }

    /**
     * Processes [WebSocketType.INITIATE_TRADE] commands.
     */
    private suspend fun handleInitiateTrade(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val gameId = requireBoundGame(session.id)
        val actorId = requireBoundPlayer(session.id)
        val payload = decodePayload(envelope, InitiateTradePayload.serializer())

        val state =
            gameService.chooseTrade(
                gameId,
                actorId,
                payload.challengedPlayerId,
                payload.animalType,
                payload.moneyCardIds,
            )

        sendStateUpdate(session, envelope.requestId, state, actorId)
        broadcastStateUpdate(gameId, state, session)
    }

    /**
     * Processes [WebSocketType.RESPOND_TO_TRADE] commands.
     */
    private suspend fun handleRespondToTrade(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val gameId = requireBoundGame(session.id)
        val actorId = requireBoundPlayer(session.id)
        val payload = decodePayload(envelope, RespondToTradePayload.serializer())

        val state =
            gameService.respondToTrade(
                gameId,
                actorId,
                payload.counterOfferedMoneyCardIds,
            )

        sendStateUpdate(session, envelope.requestId, state, actorId)
        broadcastStateUpdate(gameId, state, session)
    }

    /**
     * Processes [WebSocketType.PLACE_BID] commands.
     */
    private suspend fun handlePlaceBid(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val gameId = requireBoundGame(session.id)
        val actorId = requireBoundPlayer(session.id)
        val payload = decodePayload(envelope, PlaceBidPayload.serializer())

        val state = gameService.placeBid(gameId, actorId, payload.amount)

        sendStateUpdate(session, envelope.requestId, state, actorId)
        broadcastStateUpdate(gameId, state, session)
    }

    /**
     * Processes [WebSocketType.AUCTION_BUY_BACK] commands.
     */
    private suspend fun handleAuctionBuyBack(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val gameId = requireBoundGame(session.id)
        val actorId = requireBoundPlayer(session.id)
        val payload = decodePayload(envelope, AuctionBuyBackPayload.serializer())

        val state = gameService.resolveAuction(gameId, actorId, payload.buyBack)

        sendStateUpdate(session, envelope.requestId, state, actorId)
        broadcastStateUpdate(gameId, state, session)
    }

    /**
     * Processes [WebSocketType.FINISH_TRADE_REVEAL] commands.
     */
    private suspend fun handleFinishTradeReveal(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val gameId = requireBoundGame(session.id)
        val actorId = requireBoundPlayer(session.id)

        val state = gameService.finishTradeReveal(gameId, actorId)

        sendStateUpdate(session, envelope.requestId, state, actorId)
        broadcastStateUpdate(gameId, state, session)
    }

    /**
     * Asserts that the session is not linked to any game instance.
     */
    private fun ensureNoBoundGame(sessionId: String) {
        if (connectionRegistry.gameIdFor(sessionId) != null) {
            throw GameException(GameErrorReason.SESSION_ALREADY_BOUND_TO_GAME)
        }
    }

    /**
     * Resolves the linked game ID for a session, checking that the binding exists.
     */
    private fun requireBoundGame(sessionId: String): String =
        connectionRegistry.gameIdFor(sessionId)
            ?: throw GameException(GameErrorReason.SESSION_NOT_BOUND_TO_GAME)

    /**
     * Resolves the linked player ID for a session, checking that the binding exists.
     */
    private fun requireBoundPlayer(sessionId: String): String =
        connectionRegistry.playerIdFor(sessionId)
            ?: throw GameException(GameErrorReason.SESSION_NOT_BOUND_TO_PLAYER)

    /**
     * Safely unmarshalls a raw network text frame into a structured message envelope.
     */
    private fun decodeEnvelope(message: TextMessage): WebSocketEnvelope =
        try {
            WebSocketJson.json.decodeFromString(
                WebSocketEnvelope.serializer(),
                message.payload,
            )
        } catch (_: Exception) {
            throw GameException(GameErrorReason.INVALID_MESSAGE_FORMAT)
        }

    /**
     * Safely extracts payload from an envelope.
     */
    private fun <T> decodePayload(
        envelope: WebSocketEnvelope,
        deserializer: KSerializer<T>,
    ): T {
        val payloadJson = envelope.payload ?: throw GameException(GameErrorReason.MISSING_PAYLOAD)
        return try {
            WebSocketJson.json.decodeFromJsonElement(deserializer, payloadJson)
        } catch (_: Exception) {
            throw GameException(GameErrorReason.INVALID_PAYLOAD)
        }
    }

    /**
     * Sends [WebSocketType.GAME_STATE_UPDATED] to a single session.
     */
    private fun sendStateUpdate(
        session: WebSocketSession,
        requestId: String?,
        state: GameState,
        playerId: String,
    ) {
        send(
            session,
            WebSocketEnvelope(
                type = WebSocketType.GAME_STATE_UPDATED,
                requestId = requestId,
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        GameStatePayload.serializer(),
                        GameStatePayload(
                            state = state,
                            stateView = state.createViewForPlayer(playerId),
                        ),
                    ),
            ),
        )
    }

    /**
     * Broadcasts [WebSocketType.GAME_STATE_UPDATED] to all sessions in a game.
     */
    private fun broadcastStateUpdate(
        gameId: String,
        state: GameState,
        ignoredSession: WebSocketSession? = null,
    ) {
        val sessions = connectionRegistry.sessionsFor(gameId)

        sessions.forEach { session ->
            if (session == ignoredSession) return@forEach

            val playerId = connectionRegistry.playerIdFor(session.id) ?: return@forEach

            try {
                val envelope =
                    WebSocketEnvelope(
                        type = WebSocketType.GAME_STATE_UPDATED,
                        requestId = null,
                        payload =
                            WebSocketJson.json.encodeToJsonElement(
                                GameStatePayload.serializer(),
                                GameStatePayload(
                                    state = state,
                                    stateView = state.createViewForPlayer(playerId),
                                ),
                            ),
                    )
                val json =
                    WebSocketJson.json.encodeToString(WebSocketEnvelope.serializer(), envelope)
                val message = TextMessage(json)

                synchronized(session) {
                    if (session.isOpen) {
                        session.sendMessage(message)
                    }
                }
            } catch (e: Exception) {
                logger.error(
                    "State update broadcast failed for session ${session.id} corresponding to player $playerId",
                    e,
                )
            }
        }
    }

    /**
     * Serializes and sends an outbound envelope.
     */
    private fun send(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        synchronized(session) {
            if (session.isOpen) {
                val json =
                    WebSocketJson.json.encodeToString(WebSocketEnvelope.serializer(), envelope)
                session.sendMessage(TextMessage(json))
            }
        }
    }

    /**
     * Sends an explicit structured error envelope back down to a target.
     */
    private fun sendError(
        session: WebSocketSession,
        requestId: String?,
        reason: GameErrorReason,
    ) {
        val envelope =
            WebSocketEnvelope(
                type = WebSocketType.ERROR,
                requestId = requestId,
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        ErrorPayload.serializer(),
                        ErrorPayload(reason.name),
                    ),
            )
        send(session, envelope)
    }
}
