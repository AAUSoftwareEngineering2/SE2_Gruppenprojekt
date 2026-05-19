package at.aau.kuhhandel.server.websocket

import at.aau.kuhhandel.server.event.GameStateChangedEvent
import at.aau.kuhhandel.server.model.GameSession
import at.aau.kuhhandel.server.model.RoomActionResult
import at.aau.kuhhandel.server.service.GameService
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GameErrorReason
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
import at.aau.kuhhandel.shared.websocket.PlaceBidPayload
import at.aau.kuhhandel.shared.websocket.RespondToTradePayload
import at.aau.kuhhandel.shared.websocket.WebSocketEnvelope
import at.aau.kuhhandel.shared.websocket.WebSocketJson
import at.aau.kuhhandel.shared.websocket.WebSocketType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
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
        val gameState = GameState(phase = GamePhase.AUCTION_BIDDING)
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
        val gameState = GameState(phase = GamePhase.AUCTION_BIDDING)
        val event = GameStateChangedEvent(gameId = "game-1", newState = gameState)

        whenever(session1.isOpen).thenReturn(false)
        whenever(connectionRegistry.sessionsFor("game-1")).thenReturn(setOf(session1))

        handler.handleGameStateChanged(event)

        verify(session1, never()).sendMessage(any())
    }

    @Test
    fun `handleTextMessage sends ERROR when GameService throws`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")
        whenever(gameService.startGame("game-1", "player-1")).thenThrow(IllegalStateException())

        sendEnvelope(
            session = session1,
            type = WebSocketType.START_GAME,
            requestId = "req-1",
        )

        verify(gameService).startGame("game-1", "player-1")
        assertErrorResponse(session1, "req-1", GameErrorReason.INTERNAL_SERVER_ERROR.name)
    }

    @Test
    fun `CREATE_GAME binds session and sends GAME_CREATED`() {
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
                createdSession.state,
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

        val payload = decodePayload(response, GameCreatedPayload.serializer())

        assertEquals("game-1", payload.gameId)
        assertEquals(createdSession.state, payload.state)
    }

    @Test
    fun `CREATE_GAME with bound session sends ERROR`() {
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

        assertErrorResponse(session1, "req-1", GameErrorReason.SESSION_ALREADY_BOUND_TO_GAME.name)
    }

    @Test
    fun `START_GAME sends and broadcasts GAME_STATE_UPDATED`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")
        whenever(connectionRegistry.sessionsFor("game-1")).thenReturn(setOf(session1, session2))

        val gameState = GameState(phase = GamePhase.PLAYER_CHOICE)
        whenever(gameService.startGame("game-1", "player-1")).thenReturn(gameState)

        sendEnvelope(
            session = session1,
            type = WebSocketType.START_GAME,
            requestId = "req-1",
        )

        verify(gameService).startGame("game-1", "player-1")

        val response1 = captureResponse(session1)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
        assertEquals("req-1", response1.requestId)

        val payload1 = decodePayload(response1, GameStatePayload.serializer())

        assertEquals(gameState, payload1.state)

        val response2 = captureResponse(session2)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
        assertNull(response2.requestId)

        val payload2 = decodePayload(response2, GameStatePayload.serializer())

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
        assertErrorResponse(session1, "req-1", GameErrorReason.SESSION_NOT_BOUND_TO_GAME.name)
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

        val payload1 = decodePayload(response1, GameJoinedPayload.serializer())

        assertEquals("player-1", payload1.playerId)
        assertEquals(state, payload1.state)

        val response2 = captureResponse(session2)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
        assertNull(response2.requestId)

        val payload2 = decodePayload(response2, GameStatePayload.serializer())

        assertEquals(state, payload2.state)
    }

    @Test
    fun `JOIN_GAME with bound game sends ERROR`() {
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
        assertErrorResponse(session1, "req-1", GameErrorReason.SESSION_ALREADY_BOUND_TO_GAME.name)
    }

    @Test
    fun `JOIN_GAME with missing payload sends ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn(null)

        sendEnvelope(session = session1, type = WebSocketType.JOIN_GAME, requestId = "req-1")

        assertErrorResponse(session1, "req-1", GameErrorReason.MISSING_PAYLOAD.name)
    }

    @Test
    fun `JOIN_GAME with invalid payload sends ERROR`() {
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

        assertErrorResponse(session1, "req-1", GameErrorReason.INVALID_PAYLOAD.name)
    }

    @Test
    fun `LEAVE_GAME unbinds session, sends GAME_LEFT, and broadcasts GAME_STATE_UPDATED`() {
        val returnedState =
            GameState(
                players = listOf(PlayerState("player-2", "Player 2")),
                hostPlayerId = "player-2",
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

        val payload2 = decodePayload(response2, GameStatePayload.serializer())

        assertEquals(returnedState, payload2.state)
    }

    @Test
    fun `LEAVE_GAME with no bound game sends ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.LEAVE_GAME,
            requestId = "req-1",
        )

        verifyNoInteractions(gameService)
        assertErrorResponse(session1, "req-1", GameErrorReason.SESSION_NOT_BOUND_TO_GAME.name)
    }

    @Test
    fun `LEAVE_GAME with no bound player sends ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.LEAVE_GAME,
            requestId = "req-1",
        )

        verifyNoInteractions(gameService)
        assertErrorResponse(session1, "req-1", GameErrorReason.SESSION_NOT_BOUND_TO_PLAYER.name)
    }

    @Test
    fun `send does not send message to closed session`() {
        val returnedState =
            GameState(
                players = listOf(),
                hostPlayerId = null,
            )

        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")
        whenever(connectionRegistry.sessionsFor("game-1")).thenReturn(setOf())

        whenever(session1.isOpen).thenReturn(false)
        whenever(gameService.leaveGame("game-1", "player-1")).thenReturn(returnedState)

        sendEnvelope(
            session = session1,
            type = WebSocketType.LEAVE_GAME,
            requestId = "req-1",
        )

        verify(session1, never()).sendMessage(any())
    }

    @Test
    fun `broadcastStateUpdate ignores closed sessions`() {
        val returnedState =
            GameState(
                players = listOf(PlayerState("player-2", "Player 2")),
                hostPlayerId = "player-2",
            )

        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")
        whenever(connectionRegistry.sessionsFor("game-1")).thenReturn(setOf(session2))

        whenever(session2.isOpen).thenReturn(false)
        whenever(gameService.leaveGame("game-1", "player-1")).thenReturn(returnedState)

        sendEnvelope(
            session = session1,
            type = WebSocketType.LEAVE_GAME,
            requestId = "req-1",
        )

        verify(session2, never()).sendMessage(any())
    }

    @Test
    fun `CHOOSE_AUCTION sends and broadcasts GAME_STATE_UPDATED`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")
        whenever(connectionRegistry.sessionsFor("game-1")).thenReturn(setOf(session1, session2))

        val gameState = GameState(phase = GamePhase.AUCTION_BIDDING)
        whenever(gameService.chooseAuction("game-1", "player-1")).thenReturn(gameState)

        val envelope =
            WebSocketEnvelope(
                type = WebSocketType.CHOOSE_AUCTION,
                requestId = "req-1",
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

        verify(gameService).chooseAuction("game-1", "player-1")

        val response1 = captureResponse(session1)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
        assertEquals("req-1", response1.requestId)

        val payload1 = decodePayload(response1, GameStatePayload.serializer())

        assertEquals(gameState, payload1.state)

        val response2 = captureResponse(session2)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
        assertNull(response2.requestId)

        val payload2 = decodePayload(response2, GameStatePayload.serializer())

        assertEquals(gameState, payload2.state)
    }

    @Test
    fun `CHOOSE_AUCTION with no bound game sends ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn(null)

        val envelope =
            WebSocketEnvelope(
                type = WebSocketType.CHOOSE_AUCTION,
                requestId = "req-1",
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
        assertErrorResponse(session1, "req-1", GameErrorReason.SESSION_NOT_BOUND_TO_GAME.name)
    }

    @Test
    fun `invalid message format sends ERROR`() {
        handler.handleMessage(session1, TextMessage("HELLO"))

        verifyNoInteractions(gameService, connectionRegistry)
        verifyNoInteractions(gameService, gameService)
        assertErrorResponse(session1, null, GameErrorReason.INVALID_MESSAGE_FORMAT.name)
    }

    @Test
    fun `unsupported message type sends ERROR`() {
        val envelope =
            WebSocketEnvelope(
                type = WebSocketType.GAME_CREATED,
                requestId = "req-1",
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

        verifyNoInteractions(gameService, connectionRegistry)
        assertErrorResponse(session1, "req-1", GameErrorReason.UNSUPPORTED_MESSAGE_TYPE.name)
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
    fun `INITIATE_TRADE sends and broadcasts GAME_STATE_UPDATED`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")
        whenever(connectionRegistry.sessionsFor("game-1")).thenReturn(setOf(session1, session2))

        val gameState = GameState(phase = GamePhase.TRADE_OFFER)
        whenever(
            gameService.chooseTrade("game-1", "player-1", "player-2", AnimalType.COW, emptySet()),
        ).thenReturn(gameState)

        sendEnvelope(
            session = session1,
            type = WebSocketType.INITIATE_TRADE,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    InitiateTradePayload.serializer(),
                    InitiateTradePayload(
                        challengedPlayerId = "player-2",
                        animalType = AnimalType.COW,
                        moneyCardIds = emptySet(),
                    ),
                ),
        )

        verify(gameService).chooseTrade(
            "game-1",
            "player-1",
            "player-2",
            AnimalType.COW,
            emptySet(),
        )

        val response1 = captureResponse(session1)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
        assertEquals("req-1", response1.requestId)

        val payload1 = decodePayload(response1, GameStatePayload.serializer())

        assertEquals(gameState, payload1.state)

        val response2 = captureResponse(session2)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
        assertNull(response2.requestId)

        val payload2 = decodePayload(response2, GameStatePayload.serializer())

        assertEquals(gameState, payload2.state)
    }

    @Test
    fun `INITIATE_TRADE with no bound game sends ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.INITIATE_TRADE,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    InitiateTradePayload.serializer(),
                    InitiateTradePayload(
                        challengedPlayerId = "player-2",
                        animalType = AnimalType.COW,
                        moneyCardIds = emptySet(),
                    ),
                ),
        )

        verifyNoInteractions(gameService)
        assertErrorResponse(session1, "req-1", GameErrorReason.SESSION_NOT_BOUND_TO_GAME.name)
    }

    @Test
    fun `INITIATE_TRADE with missing payload sends ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")

        sendEnvelope(
            session = session1,
            type = WebSocketType.INITIATE_TRADE,
            requestId = "req-1",
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.MISSING_PAYLOAD.name)
    }

    @Test
    fun `INITIATE_TRADE with invalid payload sends ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")

        sendEnvelope(
            session = session1,
            type = WebSocketType.INITIATE_TRADE,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    JoinGamePayload.serializer(),
                    JoinGamePayload("game-1", "Player 1"),
                ),
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.INVALID_PAYLOAD.name)
    }

    @Test
    fun `RESPOND_TO_TRADE sends and broadcasts GAME_STATE_UPDATED`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-2")
        whenever(connectionRegistry.sessionsFor("game-1")).thenReturn(setOf(session1, session2))

        val gameState = GameState(phase = GamePhase.TRADE_REVEAL)
        whenever(gameService.respondToTrade("game-1", "player-2", emptySet())).thenReturn(gameState)

        sendEnvelope(
            session = session1,
            type = WebSocketType.RESPOND_TO_TRADE,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    RespondToTradePayload.serializer(),
                    RespondToTradePayload(
                        respondingPlayerId = "player-2",
                        counterOfferedMoneyCardIds = emptySet(),
                    ),
                ),
        )

        verify(gameService).respondToTrade("game-1", "player-2", emptySet())

        val response1 = captureResponse(session1)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
        assertEquals("req-1", response1.requestId)

        val payload1 = decodePayload(response1, GameStatePayload.serializer())

        assertEquals(gameState, payload1.state)

        val response2 = captureResponse(session2)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
        assertNull(response2.requestId)

        val payload2 = decodePayload(response2, GameStatePayload.serializer())

        assertEquals(gameState, payload2.state)
    }

    @Test
    fun `RESPOND_TO_TRADE with no bound game sends ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.RESPOND_TO_TRADE,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    RespondToTradePayload.serializer(),
                    RespondToTradePayload(
                        respondingPlayerId = "player-2",
                        counterOfferedMoneyCardIds = emptySet(),
                    ),
                ),
        )

        verifyNoInteractions(gameService)
        assertErrorResponse(session1, "req-1", GameErrorReason.SESSION_NOT_BOUND_TO_GAME.name)
    }

    @Test
    fun `RESPOND_TO_TRADE with missing payload sends ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")

        sendEnvelope(
            session = session1,
            type = WebSocketType.RESPOND_TO_TRADE,
            requestId = "req-1",
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.MISSING_PAYLOAD.name)
    }

    @Test
    fun `RESPOND_TO_TRADE with invalid payload sends ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")

        sendEnvelope(
            session = session1,
            type = WebSocketType.RESPOND_TO_TRADE,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    JoinGamePayload.serializer(),
                    JoinGamePayload("game-1", "Player 1"),
                ),
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.INVALID_PAYLOAD.name)
    }

    @Test
    fun `PLACE_BID sends and broadcasts GAME_STATE_UPDATED`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")
        whenever(connectionRegistry.sessionsFor("game-1")).thenReturn(setOf(session1, session2))

        val gameState = GameState(phase = GamePhase.AUCTION_BIDDING)
        whenever(gameService.placeBid("game-1", "player-1", 100)).thenReturn(gameState)

        sendEnvelope(
            session = session1,
            type = WebSocketType.PLACE_BID,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    PlaceBidPayload.serializer(),
                    PlaceBidPayload(amount = 100),
                ),
        )

        verify(gameService).placeBid("game-1", "player-1", 100)

        val response1 = captureResponse(session1)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
        assertEquals("req-1", response1.requestId)

        val payload1 = decodePayload(response1, GameStatePayload.serializer())

        assertEquals(gameState, payload1.state)

        val response2 = captureResponse(session2)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
        assertNull(response2.requestId)

        val payload2 = decodePayload(response2, GameStatePayload.serializer())

        assertEquals(gameState, payload2.state)
    }

    @Test
    fun `PLACE_BID with no bound game sends ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.PLACE_BID,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    PlaceBidPayload.serializer(),
                    PlaceBidPayload(amount = 100),
                ),
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.SESSION_NOT_BOUND_TO_GAME.name)
    }

    @Test
    fun `PLACE_BID with no bound player sends ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.PLACE_BID,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    PlaceBidPayload.serializer(),
                    PlaceBidPayload(amount = 100),
                ),
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.SESSION_NOT_BOUND_TO_PLAYER.name)
    }

    @Test
    fun `PLACE_BID with missing payload sends ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")

        sendEnvelope(
            session = session1,
            type = WebSocketType.PLACE_BID,
            requestId = "req-1",
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.MISSING_PAYLOAD.name)
    }

    @Test
    fun `PLACE_BID with invalid payload sends ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")

        sendEnvelope(
            session = session1,
            type = WebSocketType.PLACE_BID,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    JoinGamePayload.serializer(),
                    JoinGamePayload("game-1", "Player 1"),
                ),
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.INVALID_PAYLOAD.name)
    }

    @Test
    fun `AUCTION_BUY_BACK sends and broadcasts GAME_STATE_UPDATED`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")
        whenever(connectionRegistry.sessionsFor("game-1")).thenReturn(setOf(session1, session2))

        val gameState = GameState(phase = GamePhase.PLAYER_CHOICE)
        whenever(gameService.resolveAuction("game-1", "player-1", true)).thenReturn(gameState)

        sendEnvelope(
            session = session1,
            type = WebSocketType.AUCTION_BUY_BACK,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    AuctionBuyBackPayload.serializer(),
                    AuctionBuyBackPayload(buyBack = true),
                ),
        )

        verify(gameService).resolveAuction("game-1", "player-1", true)

        val response1 = captureResponse(session1)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
        assertEquals("req-1", response1.requestId)

        val payload1 = decodePayload(response1, GameStatePayload.serializer())

        assertEquals(gameState, payload1.state)

        val response2 = captureResponse(session2)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
        assertNull(response2.requestId)

        val payload2 = decodePayload(response2, GameStatePayload.serializer())

        assertEquals(gameState, payload2.state)
    }

    @Test
    fun `AUCTION_BUY_BACK with no bound game sends ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.AUCTION_BUY_BACK,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    AuctionBuyBackPayload.serializer(),
                    AuctionBuyBackPayload(buyBack = true),
                ),
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.SESSION_NOT_BOUND_TO_GAME.name)
    }

    @Test
    fun `AUCTION_BUY_BACK with missing payload sends ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")

        sendEnvelope(
            session = session1,
            type = WebSocketType.AUCTION_BUY_BACK,
            requestId = "req-1",
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.MISSING_PAYLOAD.name)
    }

    @Test
    fun `AUCTION_BUY_BACK with invalid payload sends ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")

        sendEnvelope(
            session = session1,
            type = WebSocketType.AUCTION_BUY_BACK,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    JoinGamePayload.serializer(),
                    JoinGamePayload("game-1", "Player 1"),
                ),
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.INVALID_PAYLOAD.name)
    }

    @Test
    fun `FINISH_TRADE_REVEAL sends and broadcasts GAME_STATE_UPDATED`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn("game-1")
        whenever(connectionRegistry.playerIdFor("session-1")).thenReturn("player-1")
        whenever(connectionRegistry.sessionsFor("game-1")).thenReturn(setOf(session1, session2))

        val gameState = GameState(phase = GamePhase.PLAYER_CHOICE)
        whenever(gameService.finishTradeReveal("game-1", "player-1")).thenReturn(gameState)

        sendEnvelope(
            session = session1,
            type = WebSocketType.FINISH_TRADE_REVEAL,
            requestId = "req-1",
        )

        verify(gameService).finishTradeReveal("game-1", "player-1")

        val response1 = captureResponse(session1)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
        assertEquals("req-1", response1.requestId)

        val payload1 = decodePayload(response1, GameStatePayload.serializer())

        assertEquals(gameState, payload1.state)

        val response2 = captureResponse(session2)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
        assertNull(response2.requestId)

        val payload2 = decodePayload(response2, GameStatePayload.serializer())

        assertEquals(gameState, payload2.state)
    }

    @Test
    fun `FINISH_TRADE_REVEAL with no bound game sends ERROR`() {
        whenever(connectionRegistry.gameIdFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.AUCTION_BUY_BACK,
            requestId = "req-1",
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.SESSION_NOT_BOUND_TO_GAME.name)
    }

    private fun sendEnvelope(
        session: WebSocketSession,
        type: WebSocketType,
        requestId: String,
        payload: JsonElement? = null,
    ) {
        val envelope = WebSocketEnvelope(type = type, requestId = requestId, payload = payload)
        handler.handleMessage(
            session,
            TextMessage(
                WebSocketJson.json.encodeToString(WebSocketEnvelope.serializer(), envelope),
            ),
        )
    }

    private fun assertErrorResponse(
        session: WebSocketSession,
        expectedRequestId: String?,
        expectedMessage: String,
    ) {
        val response = captureResponse(session)
        assertEquals(WebSocketType.ERROR, response.type)
        assertEquals(expectedRequestId, response.requestId)

        val payload = decodePayload(response, ErrorPayload.serializer())
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

    private fun <T> decodePayload(
        envelope: WebSocketEnvelope,
        deserializer: KSerializer<T>,
    ): T {
        val payloadJson = envelope.payload
        assertNotNull(payloadJson)
        return WebSocketJson.json.decodeFromJsonElement(deserializer, payloadJson)
    }
}
