package at.aau.kuhhandel.server.websocket

import at.aau.kuhhandel.server.service.GameService
import at.aau.kuhhandel.shared.websocket.CreateGamePayload
import at.aau.kuhhandel.shared.websocket.ErrorPayload
import at.aau.kuhhandel.shared.websocket.GameCreatedPayload
import at.aau.kuhhandel.shared.websocket.GameStatePayload
import at.aau.kuhhandel.shared.websocket.WebSocketEnvelope
import at.aau.kuhhandel.shared.websocket.WebSocketJson
import at.aau.kuhhandel.shared.websocket.WebSocketType
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
        val payload =
            envelope.payload?.let {
                WebSocketJson.json.decodeFromJsonElement(
                    CreateGamePayload.serializer(),
                    it,
                )
            } ?: CreateGamePayload()

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
                ?: return sendError(session, envelope.requestId, "No game bound to this connection")

        val state =
            gameService.startGame(gameId)
                ?: return sendError(session, envelope.requestId, "Game not found")

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
                ?: return sendError(session, envelope.requestId, "No game bound to this connection")

        val state =
            gameService.revealNextCard(gameId)
                ?: return sendError(session, envelope.requestId, "Game not found")

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
}
