package at.aau.kuhhandel.app.network.game

import at.aau.kuhhandel.app.data.TokenStorage
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.websocket.ErrorPayload
import at.aau.kuhhandel.shared.websocket.GameCreatedPayload
import at.aau.kuhhandel.shared.websocket.GameStatePayload
import at.aau.kuhhandel.shared.websocket.SnapshotPayload
import at.aau.kuhhandel.shared.websocket.WebSocketEnvelope
import at.aau.kuhhandel.shared.websocket.WebSocketJson
import at.aau.kuhhandel.shared.websocket.WebSocketType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameRepositoryState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val gameId: String? = null,
    val myPlayerId: String? = null,
    val gameState: GameState? = null,
    val errorMessage: String? = null,
)

/**
 * Repository responsible for managing the WebSocket connection and
 * maintaining the raw game state received from the server.
 */
class GameRepository(
    private val client: GameWebSocketClient,
    private val scope: CoroutineScope,
    private val tokenStorage: TokenStorage,
) {
    private companion object {
        const val CONNECTION_FAILED = "Connection failed"
        const val CONNECTION_LOST = "Connection lost"
    }

    private val _state = MutableStateFlow(GameRepositoryState())
    val state: StateFlow<GameRepositoryState> = _state.asStateFlow()

    private var eventsJob: Job? = null

    /** Requests to create a new game room with the given player name. */
    suspend fun createGame(playerName: String? = null) {
        ensureConnected()
        _state.update { it.copy(errorMessage = null) }
        client.createGame(playerName)
    }

    /** Requests to join an existing game room using its ID. */
    suspend fun joinGame(
        gameId: String,
        playerName: String? = null,
    ) {
        ensureConnected()
        _state.update { it.copy(gameId = gameId, errorMessage = null) }
        client.joinGame(gameId, playerName)
    }

    /** Signals the server to start the game for all connected players. */
    suspend fun startGame() {
        ensureConnected()
        _state.update { it.copy(errorMessage = null) }
        client.startGame()
    }

    /** Requests to reveal the next animal card from the deck. */
    suspend fun revealCard() {
        ensureConnected()
        _state.update { it.copy(errorMessage = null) }
        client.revealCard()
    }

    /** Submits a bid amount for the current auction. */
    suspend fun placeBid(amount: Int) {
        ensureConnected()
        _state.update { it.copy(errorMessage = null) }
        client.placeBid(amount)
    }

    /** Sends the auctioneer's choice to either buy back the animal or sell it. */
    suspend fun buyBack(buyBack: Boolean) {
        ensureConnected()
        _state.update { it.copy(errorMessage = null) }
        client.buyBack(buyBack)
    }

    /** Initiates a trade challenge against another player. */
    suspend fun initiateTrade(
        challengedPlayerId: String,
        animalType: at.aau.kuhhandel.shared.enums.AnimalType,
    ) {
        ensureConnected()
        _state.update { it.copy(errorMessage = null) }
        client.initiateTrade(challengedPlayerId, animalType)
    }

    /** Submits the initiator's selected money cards. */
    suspend fun submitTradeMoney(moneyCardIds: Set<String>) {
        ensureConnected()
        _state.update { it.copy(errorMessage = null) }
        client.submitTradeMoney(moneyCardIds)
    }

    /** Disconnects from the current game and resets local state. */
    suspend fun leaveGame() {
        ensureConnected()
        client.leaveGame()
        disconnect()
    }

    /** Sends a counter-offer in response to a trade challenge. */
    suspend fun respondToTrade(counterOfferedMoneyCardIds: Set<String> = emptySet()) {
        ensureConnected()
        _state.update { it.copy(errorMessage = null) }
        client.respondToTrade(counterOfferedMoneyCardIds)
    }

    /** Informs the server that the trade reveal animation is complete. */
    suspend fun finishTradeReveal() {
        ensureConnected()
        _state.update { it.copy(errorMessage = null) }
        client.finishTradeReveal()
    }

    /** Resets the current error message in the repository state. */
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /** Stops active connection jobs and resets the repository state. */
    fun disconnect() {
        scope.launch {
            val activeJob = eventsJob
            eventsJob = null
            activeJob?.cancel()
            client.disconnect()
            tokenStorage.clearSession()
            _state.value = GameRepositoryState()
        }
    }

    /** Ensures a WebSocket connection is active, initiating it if necessary and handling re-sync. */
    private suspend fun ensureConnected() {
        if (awaitExistingConnection()) {
            return
        }

        startConnecting()
        val events = connectEvents()
        val collectorJob = launchCollector(events)
        awaitInitialConnection(collectorJob)

        // NEW: If we were already in a game, we MUST reconnect to restore state and tell the server we're back
        val currentState = _state.value
        val gameId = currentState.gameId
        val playerId = currentState.myPlayerId
        val token = tokenStorage.getReconnectToken()

        if (gameId != null && playerId != null && token != null) {
            scope.launch {
                try {
                    // We reconnect with the same gameId and playerId.
                    // The server will recognize us if we provide identification.
                    client.reconnect(gameId, playerId, token)
                } catch (e: Exception) {
                    // Silently fail reconnect, the user might see a connection error anyway
                }
            }
        }
    }

    private suspend fun awaitExistingConnection(): Boolean {
        if (eventsJob == null) {
            return false
        }

        client.awaitConnected()
        return true
    }

    private fun startConnecting() {
        _state.update { it.copy(isConnecting = true, errorMessage = null) }
    }

    private fun connectEvents(): Flow<WebSocketEnvelope> =
        try {
            client.connect()
        } catch (e: Exception) {
            reportConnectionFailure(e)
            throw e
        }

    /** Sets up the coroutine job that collects and processes incoming WebSocket events. */
    private fun launchCollector(events: Flow<WebSocketEnvelope>): Job =
        scope
            .launch(start = CoroutineStart.LAZY) {
                collectEvents(events)
            }.also { collectorJob ->
                eventsJob = collectorJob
                collectorJob.start()
            }

    /** Core loop that collects envelopes from the stream until the connection is closed or fails. */
    private suspend fun collectEvents(events: Flow<WebSocketEnvelope>) {
        val collectorJob = currentCoroutineContext()[Job]
        try {
            events.collect(::handleEnvelope)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportCollectorFailure(e, collectorJob)
            // Try to auto-reconnect if it was a real connection error and we have a gameId
            if (eventsJob === collectorJob && _state.value.gameId != null) {
                scope.launch {
                    kotlinx.coroutines.delay(2000)
                    if (eventsJob == null) {
                        runCatching { ensureConnected() }
                    }
                }
            }
        } finally {
            finishCollector(collectorJob)
        }
    }

    /** Updates the error state when the event collector encounters an exception. */
    private fun reportCollectorFailure(
        throwable: Throwable,
        collectorJob: Job?,
    ) {
        if (eventsJob !== collectorJob) {
            return
        }

        _state.update {
            it.copy(
                errorMessage = formatThrowable(connectionErrorPrefix(), throwable),
            )
        }
    }

    private fun finishCollector(collectorJob: Job?) {
        if (eventsJob !== collectorJob) {
            return
        }

        eventsJob = null
        _state.update { it.copy(isConnecting = false, isConnected = false) }
    }

    /** Waits for the initial WebSocket connection to be established or fail. */
    private suspend fun awaitInitialConnection(collectorJob: Job) {
        try {
            client.awaitConnected()
            _state.update { it.copy(isConnecting = false, isConnected = true, errorMessage = null) }
        } catch (e: Exception) {
            cancelCollector(collectorJob)
            reportConnectionFailure(e)
            throw e
        }
    }

    private fun cancelCollector(collectorJob: Job) {
        if (eventsJob === collectorJob) {
            eventsJob = null
        }
        collectorJob.cancel()
    }

    /** Updates the state with a formatted error message when a connection attempt fails. */
    private fun reportConnectionFailure(throwable: Throwable) {
        _state.update {
            it.copy(
                isConnecting = false,
                isConnected = false,
                errorMessage = formatThrowable(CONNECTION_FAILED, throwable),
            )
        }
    }

    private fun connectionErrorPrefix(): String =
        if (_state.value.isConnected) {
            CONNECTION_LOST
        } else {
            CONNECTION_FAILED
        }

    /** Central dispatcher for all incoming WebSocket messages, updating state based on message type. */
    private fun handleEnvelope(envelope: WebSocketEnvelope) {
        when (envelope.type) {
            WebSocketType.GAME_CREATED,
            WebSocketType.GAME_JOINED,
            -> {
                val payloadJson = envelope.payload
                if (payloadJson == null) {
                    _state.update {
                        it.copy(
                            errorMessage =
                                "Invalid GAME_CREATED/JOINED message " +
                                    "(Payload is null for ${envelope.type}). Payload: null",
                        )
                    }
                    return
                }

                // 1. Try GameCreatedPayload (includes gameId)
                val created =
                    runCatching {
                        WebSocketJson.json.decodeFromJsonElement(
                            GameCreatedPayload.serializer(),
                            payloadJson,
                        )
                    }.getOrNull()

                if (created != null) {
                    tokenStorage.saveSession(
                        created.gameId,
                        created.playerId,
                        created.reconnectToken,
                    )
                    _state.update {
                        it.copy(
                            gameId = created.gameId,
                            myPlayerId = created.playerId,
                            gameState = created.state,
                            errorMessage = null,
                        )
                    }
                    return
                }

                // 2. Try GameJoinedPayload (includes playerId but NOT gameId)
                val joined =
                    runCatching {
                        WebSocketJson.json.decodeFromJsonElement(
                            at.aau.kuhhandel.shared.websocket.GameJoinedPayload
                                .serializer(),
                            payloadJson,
                        )
                    }.getOrNull()

                if (joined != null) {
                    // The repository should already have the game ID
                    val resolvedGameId = _state.value.gameId ?: ""
                    tokenStorage.saveSession(
                        resolvedGameId,
                        joined.playerId,
                        joined.reconnectToken,
                    )
                    _state.update {
                        it.copy(
                            myPlayerId = it.myPlayerId ?: joined.playerId,
                            gameState = joined.state,
                            errorMessage = null,
                        )
                    }
                    return
                }

                // 3. Fallback to GameStatePayload if the above fail (try to get at least the state)
                val gameStatePayload =
                    runCatching {
                        WebSocketJson.json.decodeFromJsonElement(
                            GameStatePayload.serializer(),
                            payloadJson,
                        )
                    }.getOrNull()

                if (gameStatePayload != null) {
                    _state.update {
                        it.copy(
                            gameState = gameStatePayload.state,
                            errorMessage = null,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            errorMessage =
                                "Invalid GAME_CREATED/JOINED message " +
                                    "(Could not decode as " +
                                    "GameCreated, GameJoined, or GameState). " +
                                    "Payload: $payloadJson",
                        )
                    }
                }
            }

            WebSocketType.GAME_STATE_UPDATED -> {
                val payload =
                    decodePayload(
                        envelope = envelope,
                        serializer = GameStatePayload.serializer(),
                        invalidMessage = "Invalid GameState message",
                    ) ?: return

                _state.update {
                    it.copy(
                        gameState = payload.state,
                        errorMessage = null,
                    )
                }
            }

            WebSocketType.SNAPSHOT -> {
                val payload =
                    decodePayload(
                        envelope = envelope,
                        serializer = SnapshotPayload.serializer(),
                        invalidMessage = "Invalid SNAPSHOT message",
                    ) ?: return

                tokenStorage.saveReconnectToken(payload.reconnectToken)

                _state.update {
                    it.copy(
                        gameState = payload.state,
                        errorMessage = null,
                    )
                }
            }

            WebSocketType.GAME_LEFT -> {
                // If we receive a GAME_LEFT, we should disconnect
            }

            WebSocketType.ERROR -> {
                val payload =
                    decodePayload(
                        envelope = envelope,
                        serializer = ErrorPayload.serializer(),
                        invalidMessage = "Invalid ERROR message",
                    ) ?: return

                _state.update { it.copy(errorMessage = payload.message) }
            }

            else -> Unit
        }
    }

    /** Safely decodes a JSON payload from an envelope into a typed object. */
    private fun <T> decodePayload(
        envelope: WebSocketEnvelope,
        serializer: kotlinx.serialization.KSerializer<T>,
        invalidMessage: String,
    ): T? =
        runCatching {
            WebSocketJson.json.decodeFromJsonElement(
                serializer,
                requireNotNull(envelope.payload) { "Payload is null for ${envelope.type}" },
            )
        }.getOrElse { throwable ->
            val payloadString =
                runCatching { envelope.payload.toString() }.getOrDefault(
                    "unavailable",
                )
            _state.update {
                it.copy(
                    errorMessage =
                        "$invalidMessage " +
                            "(${throwable.message}). Payload: $payloadString",
                )
            }
            null
        }

    private fun formatThrowable(
        prefix: String,
        throwable: Throwable,
    ): String {
        val type = throwable::class.simpleName ?: "Error"
        val message = throwable.message?.takeIf { it.isNotBlank() }
        return if (message != null) {
            "$prefix: $type - $message"
        } else {
            "$prefix: $type"
        }
    }
}
