package at.aau.kuhhandel.server.websocket

import at.aau.kuhhandel.server.event.GameStateChangedEvent
import at.aau.kuhhandel.server.exception.GameException
import at.aau.kuhhandel.server.service.GameService
import at.aau.kuhhandel.shared.enums.GameErrorReason
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.websocket.ChooseTradePayload
import at.aau.kuhhandel.shared.websocket.CreateGamePayload
import at.aau.kuhhandel.shared.websocket.ErrorPayload
import at.aau.kuhhandel.shared.websocket.GameCreatedPayload
import at.aau.kuhhandel.shared.websocket.GameJoinedPayload
import at.aau.kuhhandel.shared.websocket.GameStatePayload
import at.aau.kuhhandel.shared.websocket.JoinGamePayload
import at.aau.kuhhandel.shared.websocket.PlaceBidPayload
import at.aau.kuhhandel.shared.websocket.ReconnectPayload
import at.aau.kuhhandel.shared.websocket.ResolveAuctionPayload
import at.aau.kuhhandel.shared.websocket.RespondToTradePayload
import at.aau.kuhhandel.shared.websocket.SnapshotPayload
import at.aau.kuhhandel.shared.websocket.SpyPayload
import at.aau.kuhhandel.shared.websocket.SubmitTradeMoneyPayload
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
import java.util.UUID
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
                    WebSocketType.JOIN_GAME -> handleJoinGame(session, envelope)
                    WebSocketType.LEAVE_GAME -> handleLeaveGame(session, envelope)
                    WebSocketType.RECONNECT -> handleReconnect(session, envelope)
                    WebSocketType.START_GAME -> handleStartGame(session, envelope)
                    WebSocketType.CHOOSE_AUCTION -> handleChooseAuction(session, envelope)
                    WebSocketType.PLACE_BID -> handlePlaceBid(session, envelope)
                    WebSocketType.RESOLVE_AUCTION -> handleResolveAuction(session, envelope)
                    WebSocketType.CHOOSE_TRADE -> handleChooseTrade(session, envelope)
                    WebSocketType.SUBMIT_TRADE_MONEY -> handleSubmitTradeMoney(session, envelope)
                    WebSocketType.RESPOND_TO_TRADE -> handleRespondToTrade(session, envelope)
                    WebSocketType.SPY -> handleSpy(session, envelope)
                    WebSocketType.CATCH_SPY -> handleCatchSpy(session, envelope)
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
        connectionRegistry.bindConnection(session)
    }

    /**
     * Cleans up or disconnects an existing active socket connection.
     */
    override fun afterConnectionClosed(
        session: WebSocketSession,
        status: CloseStatus,
    ) {
        val playerSession = connectionRegistry.playerSessionFor(session.id)

        connectionRegistry.unbind(session.id)

        if (playerSession != null) {
            handlerScope.launch {
                try {
                    val state = gameService.leaveGame(playerSession.gameId, playerSession.playerId)
                    broadcastStateUpdate(playerSession.gameId, state)
                } catch (e: GameException) {
                    logger.info(
                        "Player {} could not leave game {} on disconnect. Reason: {}",
                        playerSession.playerId,
                        playerSession.gameId,
                        e.reason,
                    )
                } catch (e: Exception) {
                    logger.error(
                        "Unexpected system error when removing player {} from game {} on disconnect",
                        playerSession.playerId,
                        playerSession.gameId,
                        e,
                    )
                }
            }
        }
    }

    /**
     * Processes [WebSocketType.CREATE_GAME] commands.
     */
    private suspend fun handleCreateGame(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        ensureNoBoundPlayerSession(session.id)

        val payload = decodePayload(envelope, CreateGamePayload.serializer())

        val result = gameService.createGame(payload.playerName)
        val gameId = result.gameId
        val playerId = result.playerId

        val token = generateReconnectToken()

        connectionRegistry.bindPlayerSession(session.id, gameId, playerId, token)

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
                            reconnectToken = token,
                            state = result.gameState,
                            stateView = result.gameState.createViewForPlayer(playerId),
                        ),
                    ),
            ),
        )
    }

    /**
     * Processes [WebSocketType.JOIN_GAME] commands.
     */
    private suspend fun handleJoinGame(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        ensureNoBoundPlayerSession(session.id)
        val payload = decodePayload(envelope, JoinGamePayload.serializer())

        val gameId = payload.gameId

        val result = gameService.joinGame(gameId, payload.playerName)

        val joinedGameId = result.gameId
        val playerId = result.playerId
        val state = result.gameState

        val token = generateReconnectToken()

        connectionRegistry.bindPlayerSession(session.id, joinedGameId, playerId, token)

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
                            reconnectToken = token,
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
        val (gameId, playerId) = requireBoundPlayerSession(session.id)

        val state = gameService.leaveGame(gameId, playerId)

        connectionRegistry.unbind(session.id)

        send(
            session,
            WebSocketEnvelope(
                type = WebSocketType.GAME_LEFT,
                requestId = envelope.requestId,
            ),
        )

        broadcastStateUpdate(gameId, state)
    }

    /**
     * Processes [WebSocketType.RECONNECT] commands.
     */
    private suspend fun handleReconnect(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        ensureNoBoundPlayerSession(session.id)
        val payload = decodePayload(envelope, ReconnectPayload.serializer())

        val state = gameService.getStateForReconnection(payload.gameId, payload.playerId)

        if (!connectionRegistry.isValidToken(payload.playerId, payload.token)) {
            throw GameException(GameErrorReason.INVALID_RECONNECTION_TOKEN)
        }

        val newToken = generateReconnectToken()

        connectionRegistry.bindPlayerSession(session.id, payload.gameId, payload.playerId, newToken)

        send(
            session,
            WebSocketEnvelope(
                type = WebSocketType.SNAPSHOT,
                requestId = envelope.requestId,
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        SnapshotPayload.serializer(),
                        SnapshotPayload(
                            reconnectToken = newToken,
                            state = state,
                            stateView = state.createViewForPlayer(payload.playerId),
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
        val (gameId, actorId) = requireBoundPlayerSession(session.id)

        val state = gameService.startGame(gameId, actorId)

        sendStateUpdate(session, envelope.requestId, state, actorId)
        broadcastStateUpdate(gameId, state, session)
    }

    /**
     * Processes [WebSocketType.CHOOSE_AUCTION] commands.
     */
    private suspend fun handleChooseAuction(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val (gameId, actorId) = requireBoundPlayerSession(session.id)

        val state = gameService.chooseAuction(gameId, actorId)

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
        val (gameId, actorId) = requireBoundPlayerSession(session.id)
        val payload = decodePayload(envelope, PlaceBidPayload.serializer())

        val state = gameService.placeBid(gameId, actorId, payload.amount)

        sendStateUpdate(session, envelope.requestId, state, actorId)
        broadcastStateUpdate(gameId, state, session)
    }

    /**
     * Processes [WebSocketType.RESOLVE_AUCTION] commands.
     */
    private suspend fun handleResolveAuction(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val (gameId, actorId) = requireBoundPlayerSession(session.id)
        val payload = decodePayload(envelope, ResolveAuctionPayload.serializer())

        val state = gameService.resolveAuction(gameId, actorId, payload.buyBack)

        sendStateUpdate(session, envelope.requestId, state, actorId)
        broadcastStateUpdate(gameId, state, session)
    }

    /**
     * Processes [WebSocketType.CHOOSE_TRADE] commands.
     */
    private suspend fun handleChooseTrade(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val (gameId, actorId) = requireBoundPlayerSession(session.id)
        val payload = decodePayload(envelope, ChooseTradePayload.serializer())

        val state =
            gameService.chooseTrade(
                gameId,
                actorId,
                payload.challengedPlayerId,
                payload.animalType,
            )

        sendStateUpdate(session, envelope.requestId, state, actorId)
        broadcastStateUpdate(gameId, state, session)
    }

    /**
     * Processes [WebSocketType.SUBMIT_TRADE_MONEY] commands.
     */
    private suspend fun handleSubmitTradeMoney(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val (gameId, actorId) = requireBoundPlayerSession(session.id)
        val payload = decodePayload(envelope, SubmitTradeMoneyPayload.serializer())

        val state =
            gameService.submitTradeMoney(
                gameId,
                actorId,
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
        val (gameId, actorId) = requireBoundPlayerSession(session.id)
        val payload = decodePayload(envelope, RespondToTradePayload.serializer())

        val state =
            gameService.respondToTrade(
                gameId,
                actorId,
                payload.moneyCardIds,
            )

        sendStateUpdate(session, envelope.requestId, state, actorId)
        broadcastStateUpdate(gameId, state, session)
    }

    /**
     * Processes [WebSocketType.SPY] commands.
     */
    private suspend fun handleSpy(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val (gameId, actorId) = requireBoundPlayerSession(session.id)
        val payload = decodePayload(envelope, SpyPayload.serializer())

        val state = gameService.spy(gameId, actorId, payload.targetPlayerId)

        sendStateUpdate(session, envelope.requestId, state, actorId)
        broadcastStateUpdate(gameId, state, session)
    }

    /**
     * Processes [WebSocketType.CATCH_SPY] commands.
     */
    private suspend fun handleCatchSpy(
        session: WebSocketSession,
        envelope: WebSocketEnvelope,
    ) {
        val (gameId, actorId) = requireBoundPlayerSession(session.id)

        val state = gameService.catchSpy(gameId, actorId)

        sendStateUpdate(session, envelope.requestId, state, actorId)
        broadcastStateUpdate(gameId, state, session)
    }

    /**
     * Asserts that the WebSocket session is not linked to any player session
     */
    private fun ensureNoBoundPlayerSession(sessionId: String) {
        if (connectionRegistry.playerSessionFor(sessionId) != null) {
            throw GameException(GameErrorReason.CONNECTION_ALREADY_BOUND)
        }
    }

    /**
     * Resolves the linked player session for a WebSocket session, checking that the binding exists.
     */
    private fun requireBoundPlayerSession(sessionId: String): PlayerSession =
        connectionRegistry.playerSessionFor(sessionId)
            ?: throw GameException(GameErrorReason.CONNECTION_NOT_BOUND)

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
        val sessions = connectionRegistry.connectionsFor(gameId)

        sessions.forEach { session ->
            if (session == ignoredSession) return@forEach

            val (_, playerId) = connectionRegistry.playerSessionFor(session.id) ?: return@forEach

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

    /**
     * Generates a cryptographically secure token used to authenticate a player during reconnection.
     */
    private fun generateReconnectToken(): String = UUID.randomUUID().toString()
}
