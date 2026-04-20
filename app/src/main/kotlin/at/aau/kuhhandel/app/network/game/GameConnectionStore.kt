package at.aau.kuhhandel.app.network.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.websocket.ErrorPayload
import at.aau.kuhhandel.shared.websocket.GameCreatedPayload
import at.aau.kuhhandel.shared.websocket.GameStatePayload
import at.aau.kuhhandel.shared.websocket.WebSocketEnvelope
import at.aau.kuhhandel.shared.websocket.WebSocketJson
import at.aau.kuhhandel.shared.websocket.WebSocketType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class GameConnectionUiState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val gameId: String? = null,
    val gameState: GameState? = null,
    val errorMessage: String? = null,
)

class GameConnectionStore(
    private val client: GameWebSocketClient,
    private val scope: CoroutineScope,
) {
    var uiState by mutableStateOf(GameConnectionUiState())
        private set

    private var eventsJob: Job? = null

    suspend fun createGame(playerName: String? = null) {
        ensureConnected()
        uiState = uiState.copy(errorMessage = null)
        client.createGame(playerName)
    }

    suspend fun startGame() {
        ensureConnected()
        uiState = uiState.copy(errorMessage = null)
        client.startGame()
    }

    suspend fun revealCard() {
        ensureConnected()
        uiState = uiState.copy(errorMessage = null)
        client.revealCard()
    }

    fun clearError() {
        uiState = uiState.copy(errorMessage = null)
    }

    fun disconnect() {
        scope.launch {
            val activeJob = eventsJob
            eventsJob = null
            activeJob?.cancel()
            client.disconnect()
            uiState = GameConnectionUiState()
        }
    }

    private suspend fun ensureConnected() {
        if (eventsJob != null) {
            client.awaitConnected()
            return
        }

        uiState = uiState.copy(isConnecting = true, errorMessage = null)

        val events =
            try {
                client.connect()
            } catch (e: Exception) {
                uiState =
                    uiState.copy(
                        isConnecting = false,
                        isConnected = false,
                        errorMessage = formatThrowable("Verbindung fehlgeschlagen", e),
                    )
                throw e
            }

        val collectorJob =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    events.collect(::handleEnvelope)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (eventsJob === currentCoroutineContext()[Job]) {
                        uiState =
                            uiState.copy(
                                errorMessage =
                                    formatThrowable(
                                        if (uiState.isConnected) {
                                            "Verbindung verloren"
                                        } else {
                                            "Verbindung fehlgeschlagen"
                                        },
                                        e,
                                    ),
                            )
                    }
                } finally {
                    if (eventsJob === currentCoroutineContext()[Job]) {
                        eventsJob = null
                        uiState = uiState.copy(isConnecting = false, isConnected = false)
                    }
                }
            }

        eventsJob = collectorJob
        collectorJob.start()

        try {
            client.awaitConnected()
            uiState = uiState.copy(isConnecting = false, isConnected = true, errorMessage = null)
        } catch (e: Exception) {
            if (eventsJob === collectorJob) {
                eventsJob = null
            }
            collectorJob.cancel()
            uiState =
                uiState.copy(
                    isConnecting = false,
                    isConnected = false,
                    errorMessage = formatThrowable("Verbindung fehlgeschlagen", e),
                )
            throw e
        }
    }

    private fun handleEnvelope(envelope: WebSocketEnvelope) {
        when (envelope.type) {
            WebSocketType.GAME_CREATED -> {
                val payload =
                    decodePayload(
                        envelope = envelope,
                        serializer = GameCreatedPayload.serializer(),
                        invalidMessage = "Ungueltige GAME_CREATED-Nachricht",
                    ) ?: return

                uiState =
                    uiState.copy(
                        gameId = payload.gameId,
                        gameState = payload.state,
                        errorMessage = null,
                    )
            }

            WebSocketType.GAME_STARTED,
            WebSocketType.GAME_STATE_UPDATED,
            -> {
                val payload =
                    decodePayload(
                        envelope = envelope,
                        serializer = GameStatePayload.serializer(),
                        invalidMessage = "Ungueltige GameState-Nachricht",
                    ) ?: return

                uiState =
                    uiState.copy(
                        gameState = payload.state,
                        errorMessage = null,
                    )
            }

            WebSocketType.ERROR -> {
                val payload =
                    decodePayload(
                        envelope = envelope,
                        serializer = ErrorPayload.serializer(),
                        invalidMessage = "Ungueltige ERROR-Nachricht",
                    ) ?: return

                uiState = uiState.copy(errorMessage = payload.message)
            }

            else -> Unit
        }
    }

    private fun <T> decodePayload(
        envelope: WebSocketEnvelope,
        serializer: kotlinx.serialization.KSerializer<T>,
        invalidMessage: String,
    ): T? =
        runCatching {
            WebSocketJson.json.decodeFromJsonElement(
                serializer,
                requireNotNull(envelope.payload),
            )
        }.getOrElse {
            uiState = uiState.copy(errorMessage = invalidMessage)
            null
        }

    private fun formatThrowable(
        prefix: String,
        throwable: Throwable,
    ): String {
        val type = throwable::class.simpleName ?: "Fehler"
        val message = throwable.message?.takeIf { it.isNotBlank() }
        return if (message != null) {
            "$prefix: $type - $message"
        } else {
            "$prefix: $type"
        }
    }
}
