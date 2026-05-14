package at.aau.kuhhandel.server.websocket

import at.aau.kuhhandel.server.event.GameStateChangedEvent
import at.aau.kuhhandel.server.model.GameSession
import at.aau.kuhhandel.server.model.RoomActionResult
import at.aau.kuhhandel.server.service.GameService
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.PlayerState
import at.aau.kuhhandel.shared.websocket.AuctionBuyBackPayload
import at.aau.kuhhandel.shared.websocket.CreateGamePayload
import at.aau.kuhhandel.shared.websocket.ErrorPayload
import at.aau.kuhhandel.shared.websocket.GameCreatedPayload
import at.aau.kuhhandel.shared.websocket.GameJoinedPayload
import at.aau.kuhhandel.shared.websocket.GameStatePayload
import at.aau.kuhhandel.shared.websocket.InitiateTradePayload
import at.aau.kuhhandel.shared.websocket.JoinGamePayload
import at.aau.kuhhandel.shared.websocket.OfferTradePayload
import at.aau.kuhhandel.shared.websocket.PlaceBidPayload
import at.aau.kuhhandel.shared.websocket.RespondToTradePayload
import at.aau.kuhhandel.shared.websocket.WebSocketEnvelope
import at.aau.kuhhandel.shared.websocket.WebSocketJson
import at.aau.kuhhandel.shared.websocket.WebSocketType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.mockito.Mockito.`when` as whenever

class GameWebSocketHandlerTest {
    private lateinit var gameService: GameService
    private lateinit var connectionRegistry: ConnectionRegistry
    private lateinit var handler: GameWebSocketHandler
    private lateinit var session1: WebSocketSession
    private lateinit var session2: WebSocketSession

    @BeforeEach
    fun setUp() {
        gameService = mock(GameService::class.java)
        connectionRegistry = mock(ConnectionRegistry::class.java)
        handler = GameWebSocketHandler(gameService, connectionRegistry)

        session1 = mock(WebSocketSession::class.java)
        whenever(session1.id).thenReturn("session-1")
        whenever(session1.isOpen).thenReturn(true)

        session2 = mock(WebSocketSession::class.java)
        whenever(session2.id).thenReturn("session-2")
        whenever(session2.isOpen).thenReturn(true)
    }

    @Test
    fun `handleGameStateChanged sends GAME_STATE_UPDATED to all sessions`() {
        val gameState = GameState(phase = GamePhase.AUCTION)
        val event = GameStateChangedEvent(gameId = "game-1", newState = gameState)

        whenever(
            connectionRegistry.sessionsFor("game-1"),
        ).thenReturn(setOf(session1, session2))

        handler.handleGameStateChanged(event)

        val response1 = captureResponse(session1)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)

        val response2 = captureResponse(session2)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
    }

    @Test
    fun `handleGameStateChanged skips closed sessions`() {
        val gameState = GameState(phase = GamePhase.AUCTION)
        val event = GameStateChangedEvent(gameId = "game-1", newState = gameState)

        whenever(session1.isOpen).thenReturn(false)
        whenever(connectionRegistry.sessionsFor("game-1")).thenReturn(setOf(session1))

        handler.handleGameStateChanged(event)

        verify(session1, never()).sendMessage(any())
    }

    @Test
    fun `CREATE_GAME binds session and returns GAME_CREATED`() {
        val createdSession =
            GameSession(
                gameId = "game-1",
                hostPlayerId = "player-1",
                hostPlayerName = "Player 1",
            )

        val returnedResult =
            RoomActionResult(
                "game-1",
                "player-1",
                createdSession.gameState,
            )

        whenever(gameService.createGame("Player 1")).thenReturn(returnedResult)

        sendEnvelope(
            session = session1,
            type = WebSocketType.CREATE_GAME,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    CreateGamePayload.serializer(),
                    CreateGamePayload("Player 1"),
                ),
        )

        verify(gameService).createGame("Player 1")
        verify(connectionRegistry).bindGame("session-1", "game-1")
        verify(connectionRegistry).bindPlayer("session-1", "player-1")

        val response = captureResponse(session1)
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
    fun `CREATE_GAME with bound session returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")

        sendEnvelope(
            session = session1,
            type = WebSocketType.CREATE_GAME,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    CreateGamePayload.serializer(),
                    CreateGamePayload("Player 1"),
                ),
        )

        verifyNoInteractions(gameService)
        verify(connectionRegistry).gameIdFor("session-1")
        verify(connectionRegistry, never()).bindGame(any(), any())

        val response = captureResponse(session1)
        assertEquals(WebSocketType.ERROR, response.type)
        assertEquals("req-1", response.requestId)

        val payload =
            WebSocketJson.json.decodeFromJsonElement(
                ErrorPayload.serializer(),
                requireNotNull(response.payload),
            )

        assertEquals("This connection is already bound to a game", payload.message)
    }

    @Test
    fun `CREATE_GAME does not respond to closed session`() {
        val createdSession =
            GameSession(
                gameId = "game-1",
                hostPlayerId = "player-1",
                hostPlayerName = "Player 1",
            )

        val returnedResult =
            RoomActionResult(
                "game-1",
                "player-1",
                createdSession.gameState,
            )

        whenever(session1.isOpen).thenReturn(false)
        whenever(gameService.createGame("Player 1")).thenReturn(returnedResult)

        sendEnvelope(
            session = session1,
            type = WebSocketType.CREATE_GAME,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    CreateGamePayload.serializer(),
                    CreateGamePayload("Player 1"),
                ),
        )

        verify(session1, never()).sendMessage(any())
    }

    @Test
    fun `START_GAME broadcasts GAME_STATE_UPDATED`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.sessionsFor("game-1")).thenReturn(setOf(session1, session2))

        val gameState = GameState(phase = GamePhase.PLAYER_TURN)
        whenever(gameService.startGame("game-1")).thenReturn(gameState)

        sendEnvelope(
            session = session1,
            type = WebSocketType.START_GAME,
            requestId = "req-2",
        )

        verify(gameService).startGame("game-1")

        val response1 = captureResponse(session1)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
        assertEquals("req-2", response1.requestId)

        val payload1 =
            WebSocketJson.json.decodeFromJsonElement(
                GameStatePayload.serializer(),
                requireNotNull(response1.payload),
            )

        assertEquals(gameState, payload1.state)

        val response2 = captureResponse(session2)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
        assertNull(response2.requestId)

        val payload2 =
            WebSocketJson.json.decodeFromJsonElement(
                GameStatePayload.serializer(),
                requireNotNull(response2.payload),
            )

        assertEquals(gameState, payload2.state)
    }

    @Test
    fun `START_GAME with no bound game sends ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.START_GAME,
            requestId = "req-1",
        )

        verifyNoInteractions(gameService)

        val response = captureResponse(session1)
        assertEquals(WebSocketType.ERROR, response.type)
        assertEquals("req-1", response.requestId)

        val payload =
            WebSocketJson.json.decodeFromJsonElement(
                ErrorPayload.serializer(),
                requireNotNull(response.payload),
            )

        assertEquals("No game bound to this connection", payload.message)
    }

    @Test
    fun `START_GAME with missing game returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(gameService.startGame("game-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.START_GAME,
            requestId = "req-2",
        )

        verify(gameService).startGame("game-1")

        val response = captureResponse(session1)
        assertEquals(WebSocketType.ERROR, response.type)
        assertEquals("req-2", response.requestId)

        val payload =
            WebSocketJson.json.decodeFromJsonElement(
                ErrorPayload.serializer(),
                requireNotNull(response.payload),
            )

        assertEquals("Game not found", payload.message)
    }

    @Test
    fun `JOIN_GAME binds game and player, sends GAME_JOINED, and broadcasts GAME_STATE_UPDATED`() {
        val state =
            GameState(
                players = listOf(PlayerState("player-1", "Player 1")),
                hostPlayerId = "player-1",
            )

        val returnedResult =
            RoomActionResult(
                "game-1",
                "player-1",
                state,
            )

        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn(null)
        whenever(connectionRegistry.sessionsFor("game-1")).thenReturn(setOf(session1, session2))

        whenever(
            gameService.joinGame("game-1", "Player 1"),
        ).thenReturn(returnedResult)

        sendEnvelope(
            session = session1,
            type = WebSocketType.JOIN_GAME,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    JoinGamePayload.serializer(),
                    JoinGamePayload("game-1", "Player 1"),
                ),
        )

        verify(gameService).joinGame("game-1", "Player 1")
        verify(connectionRegistry).bindGame("session-1", "game-1")
        verify(connectionRegistry).bindPlayer("session-1", "player-1")

        val response1 = captureResponse(session1)
        assertEquals(WebSocketType.GAME_JOINED, response1.type)
        assertEquals("req-1", response1.requestId)

        val payload1 =
            WebSocketJson.json.decodeFromJsonElement(
                GameJoinedPayload.serializer(),
                requireNotNull(response1.payload),
            )

        assertEquals("player-1", payload1.playerId)
        assertEquals(state, payload1.state)

        val response2 = captureResponse(session2)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
        assertNull(response2.requestId)

        val payload2 =
            WebSocketJson.json.decodeFromJsonElement(
                GameStatePayload.serializer(),
                requireNotNull(response2.payload),
            )

        assertEquals(state, payload2.state)
    }

    @Test
    fun `JOIN_GAME with bound game returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")

        sendEnvelope(
            session = session1,
            type = WebSocketType.JOIN_GAME,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    JoinGamePayload.serializer(),
                    JoinGamePayload("game-2", "Player 1"),
                ),
        )

        verifyNoInteractions(gameService)
        assertErrorResponse("This connection is already bound to a game")
    }

    @Test
    fun `JOIN_GAME with missing payload returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn(null)

        sendEnvelope(session = session1, type = WebSocketType.JOIN_GAME, requestId = "req-1")

        assertErrorResponse("Missing payload for JOIN_GAME")
    }

    @Test
    fun `JOIN_GAME with invalid payload returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.JOIN_GAME,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    CreateGamePayload.serializer(),
                    CreateGamePayload("Player 1"),
                ),
        )

        assertErrorResponse("Invalid payload for JOIN_GAME")
    }

    @Test
    fun `LEAVE_GAME unbinds session, sends GAME_LEFT, and broadcasts GAME_STATE_UPDATED`() {
        val returnedState =
            GameState(
                players = listOf(PlayerState("player-1", "Player 1")),
                hostPlayerId = "player-1",
            )

        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")
        whenever(connectionRegistry.sessionsFor("game-1")).thenReturn(setOf(session2))

        whenever(gameService.leaveGame("game-1", "player-1")).thenReturn(returnedState)

        sendEnvelope(
            session = session1,
            type = WebSocketType.LEAVE_GAME,
            requestId = "req-1",
        )

        verify(gameService).leaveGame("game-1", "player-1")
        verify(connectionRegistry).unbind("session-1")

        val response1 = captureResponse(session1)
        assertEquals(WebSocketType.GAME_LEFT, response1.type)
        assertEquals("req-1", response1.requestId)

        val response2 = captureResponse(session2)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
        assertNull(response2.requestId)

        val payload2 =
            WebSocketJson.json.decodeFromJsonElement(
                GameStatePayload.serializer(),
                requireNotNull(response2.payload),
            )

        assertEquals(returnedState, payload2.state)
    }

    @Test
    fun `LEAVE_GAME with no bound game returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.LEAVE_GAME,
            requestId = "req-1",
        )

        verifyNoInteractions(gameService)
        assertErrorResponse("No game bound to this connection")
    }

    @Test
    fun `LEAVE_GAME with no bound player returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.LEAVE_GAME,
            requestId = "req-1",
        )

        verifyNoInteractions(gameService)
        assertErrorResponse("No player bound to this connection")
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
            session1,
            TextMessage(
                WebSocketJson.json.encodeToString(
                    WebSocketEnvelope.serializer(),
                    envelope,
                ),
            ),
        )

        verify(gameService).revealNextCard("game-1")

        val response = captureResponse(session1)
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
    fun `REVEAL_CARD with no bound game returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn(null)

        val envelope =
            WebSocketEnvelope(
                type = WebSocketType.REVEAL_CARD,
                requestId = "req-3",
            )

        handler.handleMessage(
            session1,
            TextMessage(
                WebSocketJson.json.encodeToString(
                    WebSocketEnvelope.serializer(),
                    envelope,
                ),
            ),
        )

        verifyNoInteractions(gameService)

        val response = captureResponse(session1)
        assertEquals(WebSocketType.ERROR, response.type)
        assertEquals("req-3", response.requestId)

        val payload =
            WebSocketJson.json.decodeFromJsonElement(
                ErrorPayload.serializer(),
                requireNotNull(response.payload),
            )

        assertEquals("No game bound to this connection", payload.message)
    }

    @Test
    fun `REVEAL_CARD with missing game returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(gameService.revealNextCard("game-1")).thenReturn(null)

        val envelope =
            WebSocketEnvelope(
                type = WebSocketType.REVEAL_CARD,
                requestId = "req-4",
            )

        handler.handleMessage(
            session1,
            TextMessage(
                WebSocketJson.json.encodeToString(
                    WebSocketEnvelope.serializer(),
                    envelope,
                ),
            ),
        )

        verify(gameService).revealNextCard("game-1")

        val response = captureResponse(session1)
        assertEquals(WebSocketType.ERROR, response.type)
        assertEquals("req-4", response.requestId)

        val payload =
            WebSocketJson.json.decodeFromJsonElement(
                ErrorPayload.serializer(),
                requireNotNull(response.payload),
            )

        assertEquals("Game not found", payload.message)
    }

    @Test
    fun `invalid message format returns ERROR`() {
        handler.handleMessage(session1, TextMessage("HELLO"))

        val response = captureResponse(session1)
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
            session1,
            TextMessage(
                WebSocketJson.json.encodeToString(
                    WebSocketEnvelope.serializer(),
                    envelope,
                ),
            ),
        )

        val response = captureResponse(session1)
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

    @Test
    fun `afterConnectionEstablished binds session`() {
        handler.afterConnectionEstablished(session1)

        verify(connectionRegistry).bindSession(session1)
    }

    @Test
    fun `afterConnectionClosed unbinds session`() {
        handler.afterConnectionClosed(session1, CloseStatus.NORMAL)

        verify(connectionRegistry).unbind("session-1")
    }

    @Test
    fun `INITIATE_TRADE happy path broadcasts GAME_STATE_UPDATED`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.sessionsFor("game-1")).thenReturn(setOf(session1, session2))

        val gameState = GameState(phase = GamePhase.TRADE)
        whenever(
            gameService.chooseTrade("game-1", "player-2", AnimalType.COW, emptyList()),
        ).thenReturn(gameState)

        sendEnvelope(
            session = session1,
            type = WebSocketType.INITIATE_TRADE,
            requestId = "req-trade-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    InitiateTradePayload.serializer(),
                    InitiateTradePayload(
                        challengedPlayerId = "player-2",
                        animalType = AnimalType.COW,
                        moneyCardIds = emptyList(),
                    ),
                ),
        )

        verify(gameService).chooseTrade("game-1", "player-2", AnimalType.COW, emptyList())

        val response1 = captureResponse(session1)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
        assertEquals("req-trade-1", response1.requestId)

        val payload1 =
            WebSocketJson.json.decodeFromJsonElement(
                GameStatePayload.serializer(),
                requireNotNull(response1.payload),
            )

        assertEquals(gameState, payload1.state)

        val response2 = captureResponse(session2)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
        assertNull(response2.requestId)

        val payload2 =
            WebSocketJson.json.decodeFromJsonElement(
                GameStatePayload.serializer(),
                requireNotNull(response2.payload),
            )

        assertEquals(gameState, payload2.state)
    }

    @Test
    fun `INITIATE_TRADE without bound game returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.INITIATE_TRADE,
            requestId = "req-trade-2",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    InitiateTradePayload.serializer(),
                    InitiateTradePayload(
                        challengedPlayerId = "player-2",
                        animalType = AnimalType.COW,
                        moneyCardIds = emptyList(),
                    ),
                ),
        )

        verifyNoInteractions(gameService)
        assertErrorResponse("No game bound to this connection")
    }

    @Test
    fun `INITIATE_TRADE with missing payload returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")

        sendEnvelope(
            session = session1,
            type = WebSocketType.INITIATE_TRADE,
            requestId = "req-trade-3",
        )

        assertErrorResponse("Missing payload for INITIATE_TRADE")
    }

    @Test
    fun `INITIATE_TRADE with invalid payload returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")

        sendEnvelope(
            session = session1,
            type = WebSocketType.INITIATE_TRADE,
            requestId = "req-trade-4",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    OfferTradePayload.serializer(),
                    OfferTradePayload(moneyCardIds = listOf("m-1")),
                ),
        )

        assertErrorResponse("Invalid payload for INITIATE_TRADE")
    }

    @Test
    fun `INITIATE_TRADE when service rejects with IllegalArgument returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(gameService.chooseTrade("game-1", "player-2", AnimalType.COW, emptyList()))
            .thenThrow(IllegalArgumentException("Unknown challenged player player-2"))

        sendEnvelope(
            session = session1,
            type = WebSocketType.INITIATE_TRADE,
            requestId = "req-trade-5",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    InitiateTradePayload.serializer(),
                    InitiateTradePayload(
                        challengedPlayerId = "player-2",
                        animalType = AnimalType.COW,
                        moneyCardIds = emptyList(),
                    ),
                ),
        )

        assertErrorResponse("Unknown challenged player player-2")
    }

    @Test
    fun `INITIATE_TRADE when service rejects with IllegalState returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(gameService.chooseTrade("game-1", "player-2", AnimalType.COW, emptyList()))
            .thenThrow(IllegalStateException("Cannot start a trade during phase NOT_STARTED"))

        sendEnvelope(
            session = session1,
            type = WebSocketType.INITIATE_TRADE,
            requestId = "req-trade-6",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    InitiateTradePayload.serializer(),
                    InitiateTradePayload(
                        challengedPlayerId = "player-2",
                        animalType = AnimalType.COW,
                        moneyCardIds = emptyList(),
                    ),
                ),
        )

        assertErrorResponse("Cannot start a trade during phase NOT_STARTED")
    }

    @Test
    fun `INITIATE_TRADE with missing game returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(
            gameService.chooseTrade("game-1", "player-2", AnimalType.COW, emptyList()),
        ).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.INITIATE_TRADE,
            requestId = "req-trade-7",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    InitiateTradePayload.serializer(),
                    InitiateTradePayload(
                        challengedPlayerId = "player-2",
                        animalType = AnimalType.COW,
                        moneyCardIds = emptyList(),
                    ),
                ),
        )

        assertErrorResponse("Game not found")
    }

    @Test
    fun `OFFER_TRADE happy path broadcasts GAME_STATE_UPDATED`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.sessionsFor("game-1")).thenReturn(setOf(session1, session2))

        val gameState = GameState(phase = GamePhase.TRADE)
        whenever(gameService.offerTrade("game-1", listOf("m-10"))).thenReturn(gameState)

        sendEnvelope(
            session = session1,
            type = WebSocketType.OFFER_TRADE,
            requestId = "req-offer-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    OfferTradePayload.serializer(),
                    OfferTradePayload(moneyCardIds = listOf("m-10")),
                ),
        )

        verify(gameService).offerTrade("game-1", listOf("m-10"))

        val response1 = captureResponse(session1)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
        assertEquals("req-offer-1", response1.requestId)

        val payload1 =
            WebSocketJson.json.decodeFromJsonElement(
                GameStatePayload.serializer(),
                requireNotNull(response1.payload),
            )

        assertEquals(gameState, payload1.state)

        val response2 = captureResponse(session2)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
        assertNull(response2.requestId)

        val payload2 =
            WebSocketJson.json.decodeFromJsonElement(
                GameStatePayload.serializer(),
                requireNotNull(response2.payload),
            )

        assertEquals(gameState, payload2.state)
    }

    @Test
    fun `OFFER_TRADE without bound game returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.OFFER_TRADE,
            requestId = "req-offer-2",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    OfferTradePayload.serializer(),
                    OfferTradePayload(moneyCardIds = listOf("m-10")),
                ),
        )

        verifyNoInteractions(gameService)
        assertErrorResponse("No game bound to this connection")
    }

    @Test
    fun `OFFER_TRADE when service rejects with IllegalArgument returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(gameService.offerTrade("game-1", listOf("m-10")))
            .thenThrow(IllegalArgumentException("Player player-1 does not own money card m-10"))

        sendEnvelope(
            session = session1,
            type = WebSocketType.OFFER_TRADE,
            requestId = "req-offer-3",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    OfferTradePayload.serializer(),
                    OfferTradePayload(moneyCardIds = listOf("m-10")),
                ),
        )

        assertErrorResponse("Player player-1 does not own money card m-10")
    }

    @Test
    fun `OFFER_TRADE when service rejects with IllegalState returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(gameService.offerTrade("game-1", listOf("m-10")))
            .thenThrow(IllegalStateException("Cannot offer money for a trade during phase AUCTION"))

        sendEnvelope(
            session = session1,
            type = WebSocketType.OFFER_TRADE,
            requestId = "req-offer-4",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    OfferTradePayload.serializer(),
                    OfferTradePayload(moneyCardIds = listOf("m-10")),
                ),
        )

        assertErrorResponse("Cannot offer money for a trade during phase AUCTION")
    }

    @Test
    fun `OFFER_TRADE with missing game returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(gameService.offerTrade("game-1", listOf("m-10"))).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.OFFER_TRADE,
            requestId = "req-offer-5",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    OfferTradePayload.serializer(),
                    OfferTradePayload(moneyCardIds = listOf("m-10")),
                ),
        )

        assertErrorResponse("Game not found")
    }

    @Test
    fun `RESPOND_TO_TRADE happy path broadcasts GAME_STATE_UPDATED`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.sessionsFor("game-1")).thenReturn(setOf(session1, session2))

        val gameState = GameState(phase = GamePhase.ROUND_END)
        whenever(gameService.respondToTrade("game-1", "player-2", true)).thenReturn(gameState)

        sendEnvelope(
            session = session1,
            type = WebSocketType.RESPOND_TO_TRADE,
            requestId = "req-resp-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    RespondToTradePayload.serializer(),
                    RespondToTradePayload(
                        respondingPlayerId = "player-2",
                        accepted = true,
                        counterOfferedMoneyCardIds = emptyList(), // re-check this!
                    ),
                ),
        )

        verify(gameService).respondToTrade("game-1", "player-2", true)

        val response1 = captureResponse(session1)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
        assertEquals("req-resp-1", response1.requestId)

        val payload1 =
            WebSocketJson.json.decodeFromJsonElement(
                GameStatePayload.serializer(),
                requireNotNull(response1.payload),
            )

        assertEquals(gameState, payload1.state)

        val response2 = captureResponse(session2)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
        assertNull(response2.requestId)

        val payload2 =
            WebSocketJson.json.decodeFromJsonElement(
                GameStatePayload.serializer(),
                requireNotNull(response2.payload),
            )

        assertEquals(gameState, payload2.state)
    }

    @Test
    fun `RESPOND_TO_TRADE without bound game returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.RESPOND_TO_TRADE,
            requestId = "req-resp-2",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    RespondToTradePayload.serializer(),
                    RespondToTradePayload(
                        respondingPlayerId = "player-2",
                        accepted = false,
                        counterOfferedMoneyCardIds = emptyList(), // re-check this!
                    ),
                ),
        )

        verifyNoInteractions(gameService)
        assertErrorResponse("No game bound to this connection")
    }

    @Test
    fun `RESPOND_TO_TRADE when service rejects with IllegalArgument returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        val errorMessage = "Only the challenged player can respond to the trade"
        whenever(gameService.respondToTrade("game-1", "player-1", true))
            .thenThrow(IllegalArgumentException(errorMessage))

        sendEnvelope(
            session = session1,
            type = WebSocketType.RESPOND_TO_TRADE,
            requestId = "req-resp-3",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    RespondToTradePayload.serializer(),
                    RespondToTradePayload(
                        respondingPlayerId = "player-1",
                        accepted = true,
                        counterOfferedMoneyCardIds = emptyList(), // re-check this!
                    ),
                ),
        )

        assertErrorResponse("Only the challenged player can respond to the trade")
    }

    @Test
    fun `RESPOND_TO_TRADE when service rejects with IllegalState returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(gameService.respondToTrade("game-1", "player-2", true))
            .thenThrow(IllegalStateException("Cannot respond to a trade during phase AUCTION"))

        sendEnvelope(
            session = session1,
            type = WebSocketType.RESPOND_TO_TRADE,
            requestId = "req-resp-4",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    RespondToTradePayload.serializer(),
                    RespondToTradePayload(
                        respondingPlayerId = "player-2",
                        accepted = true,
                        counterOfferedMoneyCardIds = emptyList(), // re-check this!
                    ),
                ),
        )

        assertErrorResponse("Cannot respond to a trade during phase AUCTION")
    }

    @Test
    fun `RESPOND_TO_TRADE with missing game returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(gameService.respondToTrade("game-1", "player-2", false)).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.RESPOND_TO_TRADE,
            requestId = "req-resp-5",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    RespondToTradePayload.serializer(),
                    RespondToTradePayload(
                        respondingPlayerId = "player-2",
                        accepted = false,
                        counterOfferedMoneyCardIds = emptyList(), // re-check this!
                    ),
                ),
        )

        assertErrorResponse("Game not found")
    }

    @Test
    fun `PLACE_BID happy path broadcasts GAME_STATE_UPDATED`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")
        whenever(connectionRegistry.sessionsFor("game-1")).thenReturn(setOf(session1, session2))

        val gameState = GameState(phase = GamePhase.AUCTION)
        whenever(gameService.placeBid("game-1", "player-1", 100)).thenReturn(gameState)

        sendEnvelope(
            session = session1,
            type = WebSocketType.PLACE_BID,
            requestId = "req-bid-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    PlaceBidPayload.serializer(),
                    PlaceBidPayload(amount = 100),
                ),
        )

        verify(gameService).placeBid("game-1", "player-1", 100)

        val response1 = captureResponse(session1)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
        assertEquals("req-bid-1", response1.requestId)

        val payload1 =
            WebSocketJson.json.decodeFromJsonElement(
                GameStatePayload.serializer(),
                requireNotNull(response1.payload),
            )

        assertEquals(gameState, payload1.state)

        val response2 = captureResponse(session2)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
        assertNull(response2.requestId)

        val payload2 =
            WebSocketJson.json.decodeFromJsonElement(
                GameStatePayload.serializer(),
                requireNotNull(response2.payload),
            )

        assertEquals(gameState, payload2.state)
    }

    @Test
    fun `PLACE_BID with missing player bound returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.PLACE_BID,
            requestId = "req-bid-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    PlaceBidPayload.serializer(),
                    PlaceBidPayload(amount = 100),
                ),
        )

        assertErrorResponse("No player bound to this connection")
    }

    @Test
    fun `PLACE_BID when service throws exception returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")
        whenever(
            gameService.placeBid("game-1", "player-1", 100),
        ).thenThrow(RuntimeException("Too low"))

        sendEnvelope(
            session = session1,
            type = WebSocketType.PLACE_BID,
            requestId = "req-bid-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    PlaceBidPayload.serializer(),
                    PlaceBidPayload(amount = 100),
                ),
        )

        assertErrorResponse("Too low")
    }

    @Test
    fun `PLACE_BID with missing game returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")
        whenever(gameService.placeBid("game-1", "player-1", 100)).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.PLACE_BID,
            requestId = "req-bid-2",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    PlaceBidPayload.serializer(),
                    PlaceBidPayload(amount = 100),
                ),
        )

        assertErrorResponse("Game not found")
    }

    @Test
    fun `AUCTION_BUY_BACK happy path broadcasts GAME_STATE_UPDATED`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.sessionsFor("game-1")).thenReturn(setOf(session1, session2))

        val gameState = GameState(phase = GamePhase.ROUND_END)
        whenever(gameService.resolveAuction("game-1", true)).thenReturn(gameState)

        sendEnvelope(
            session = session1,
            type = WebSocketType.AUCTION_BUY_BACK,
            requestId = "req-buyback-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    AuctionBuyBackPayload.serializer(),
                    AuctionBuyBackPayload(buyBack = true),
                ),
        )

        verify(gameService).resolveAuction("game-1", true)

        val response1 = captureResponse(session1)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
        assertEquals("req-buyback-1", response1.requestId)

        val payload1 =
            WebSocketJson.json.decodeFromJsonElement(
                GameStatePayload.serializer(),
                requireNotNull(response1.payload),
            )

        assertEquals(gameState, payload1.state)

        val response2 = captureResponse(session2)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
        assertNull(response2.requestId)

        val payload2 =
            WebSocketJson.json.decodeFromJsonElement(
                GameStatePayload.serializer(),
                requireNotNull(response2.payload),
            )

        assertEquals(gameState, payload2.state)
    }

    @Test
    fun `AUCTION_BUY_BACK with missing game returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(gameService.resolveAuction("game-1", true)).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.AUCTION_BUY_BACK,
            requestId = "req-buyback-2",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    AuctionBuyBackPayload.serializer(),
                    AuctionBuyBackPayload(buyBack = true),
                ),
        )

        assertErrorResponse("Game not found")
    }

    @Test
    fun `AUCTION_BUY_BACK when service throws returns ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(gameService.resolveAuction("game-1", true)).thenThrow(RuntimeException("Error"))

        sendEnvelope(
            session = session1,
            type = WebSocketType.AUCTION_BUY_BACK,
            requestId = "req-buyback-3",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    AuctionBuyBackPayload.serializer(),
                    AuctionBuyBackPayload(buyBack = true),
                ),
        )

        assertErrorResponse("Error")
    }

    private fun sendEnvelope(
        session: WebSocketSession,
        type: WebSocketType,
        requestId: String,
        payload: kotlinx.serialization.json.JsonElement? = null,
    ) {
        val envelope = WebSocketEnvelope(type = type, requestId = requestId, payload = payload)
        handler.handleMessage(
            session,
            TextMessage(
                WebSocketJson.json.encodeToString(WebSocketEnvelope.serializer(), envelope),
            ),
        )
    }

    private fun assertErrorResponse(expectedMessage: String) {
        val response = captureResponse(session1)
        assertEquals(WebSocketType.ERROR, response.type)
        val payload =
            WebSocketJson.json.decodeFromJsonElement(
                ErrorPayload.serializer(),
                requireNotNull(response.payload),
            )
        assertEquals(expectedMessage, payload.message)
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
