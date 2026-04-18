package at.aau.kuhhandel.server.websocket

import at.aau.kuhhandel.server.model.GameSession
import at.aau.kuhhandel.server.service.GameService
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.websocket.CreateGamePayload
import at.aau.kuhhandel.shared.websocket.ErrorPayload
import at.aau.kuhhandel.shared.websocket.GameCreatedPayload
import at.aau.kuhhandel.shared.websocket.GameStatePayload
import at.aau.kuhhandel.shared.websocket.WebSocketEnvelope
import at.aau.kuhhandel.shared.websocket.WebSocketJson
import at.aau.kuhhandel.shared.websocket.WebSocketType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.any
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.mockito.Mockito.`when` as whenever

class GameWebSocketHandlerTest {
    private lateinit var gameService: GameService
    private lateinit var connectionRegistry: ConnectionRegistry
    private lateinit var handler: GameWebSocketHandler
    private lateinit var session: WebSocketSession

    @BeforeEach
    fun setUp() {
        gameService = mock(GameService::class.java)
        connectionRegistry = mock(ConnectionRegistry::class.java)
        handler = GameWebSocketHandler(gameService, connectionRegistry)

        session = mock(WebSocketSession::class.java)
        whenever(session.id).thenReturn("session-1")
    }

    @Test
    fun `CREATE_GAME binds session and returns GAME_CREATED`() {
        val createdSession =
            GameSession(
                gameId = "game-1",
                playerId = "player-1",
            )

        whenever(gameService.createGame(any())).thenReturn(createdSession)

        val envelope =
            WebSocketEnvelope(
                type = WebSocketType.CREATE_GAME,
                requestId = "req-1",
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        CreateGamePayload.serializer(),
                        CreateGamePayload(),
                    ),
            )

        handler.handleMessage(
            session,
            TextMessage(
                WebSocketJson.json.encodeToString(
                    WebSocketEnvelope.serializer(),
                    envelope,
                ),
            ),
        )

        verify(gameService).createGame(any())
        verify(connectionRegistry).bind("session-1", "game-1")

        val response = captureResponse(session)
        assertEquals(WebSocketType.GAME_CREATED, response.type)
        assertEquals("req-1", response.requestId)

        val payload =
            WebSocketJson.json.decodeFromJsonElement(
                GameCreatedPayload.serializer(),
                requireNotNull(response.payload),
            )

        assertEquals("game-1", payload.gameId)
        assertEquals(createdSession.gameState, payload.state)
    }

    @Test
    fun `START_GAME returns GAME_STARTED`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")

        val gameState = GameState(phase = GamePhase.PLAYER_TURN)
        whenever(gameService.startGame("game-1")).thenReturn(gameState)

        val envelope =
            WebSocketEnvelope(
                type = WebSocketType.START_GAME,
                requestId = "req-2",
            )

        handler.handleMessage(
            session,
            TextMessage(
                WebSocketJson.json.encodeToString(
                    WebSocketEnvelope.serializer(),
                    envelope,
                ),
            ),
        )

        verify(gameService).startGame("game-1")

        val response = captureResponse(session)
        assertEquals(WebSocketType.GAME_STARTED, response.type)
        assertEquals("req-2", response.requestId)

        val payload =
            WebSocketJson.json.decodeFromJsonElement(
                GameStatePayload.serializer(),
                requireNotNull(response.payload),
            )

        assertEquals(gameState, payload.state)
    }

    @Test
    fun `REVEAL_CARD returns GAME_STATE_UPDATED`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")

        val gameState = GameState(phase = GamePhase.PLAYER_TURN)
        whenever(gameService.revealNextCard("game-1")).thenReturn(gameState)

        val envelope =
            WebSocketEnvelope(
                type = WebSocketType.REVEAL_CARD,
                requestId = "req-3",
            )

        handler.handleMessage(
            session,
            TextMessage(
                WebSocketJson.json.encodeToString(
                    WebSocketEnvelope.serializer(),
                    envelope,
                ),
            ),
        )

        verify(gameService).revealNextCard("game-1")

        val response = captureResponse(session)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response.type)
        assertEquals("req-3", response.requestId)

        val payload =
            WebSocketJson.json.decodeFromJsonElement(
                GameStatePayload.serializer(),
                requireNotNull(response.payload),
            )

        assertEquals(gameState, payload.state)
    }

    @Test
    fun `invalid message format returns ERROR`() {
        handler.handleMessage(session, TextMessage("HELLO"))

        val response = captureResponse(session)
        assertEquals(WebSocketType.ERROR, response.type)

        val payload =
            WebSocketJson.json.decodeFromJsonElement(
                ErrorPayload.serializer(),
                requireNotNull(response.payload),
            )

        assertEquals("Invalid message format", payload.message)

        verifyNoInteractions(gameService, connectionRegistry)
    }

    @Test
    fun `unsupported server event type returns ERROR`() {
        val envelope =
            WebSocketEnvelope(
                type = WebSocketType.GAME_CREATED,
                requestId = "req-unsupported",
            )

        handler.handleMessage(
            session,
            TextMessage(
                WebSocketJson.json.encodeToString(
                    WebSocketEnvelope.serializer(),
                    envelope,
                ),
            ),
        )

        val response = captureResponse(session)
        assertEquals(WebSocketType.ERROR, response.type)
        assertEquals("req-unsupported", response.requestId)

        val payload =
            WebSocketJson.json.decodeFromJsonElement(
                ErrorPayload.serializer(),
                requireNotNull(response.payload),
            )

        assertEquals("Unsupported message type", payload.message)

        verifyNoInteractions(gameService, connectionRegistry)
    }

    private fun captureResponse(session: WebSocketSession): WebSocketEnvelope {
        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(session).sendMessage(captor.capture())

        return WebSocketJson.json.decodeFromString(
            WebSocketEnvelope.serializer(),
            captor.value.payload,
        )
    }
}
