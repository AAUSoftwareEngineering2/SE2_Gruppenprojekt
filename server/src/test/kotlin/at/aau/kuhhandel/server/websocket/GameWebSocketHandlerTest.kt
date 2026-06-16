@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package at.aau.kuhhandel.server.websocket

import at.aau.kuhhandel.server.event.GameStateChangedEvent
import at.aau.kuhhandel.server.model.GameSession
import at.aau.kuhhandel.server.model.RoomActionResult
import at.aau.kuhhandel.server.service.GameService
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GameErrorReason
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.Player
import at.aau.kuhhandel.shared.model.SpyAction
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
import at.aau.kuhhandel.shared.websocket.SubmitAuctionPaymentPayload
import at.aau.kuhhandel.shared.websocket.SubmitTradeMoneyPayload
import at.aau.kuhhandel.shared.websocket.WebSocketEnvelope
import at.aau.kuhhandel.shared.websocket.WebSocketJson
import at.aau.kuhhandel.shared.websocket.WebSocketType
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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

    private val testDispatcher = UnconfinedTestDispatcher()

    private val baseState =
        GameState(
            players =
                listOf(
                    Player("player-1", "Player1"),
                    Player("player-2", "Player2"),
                ),
            hostPlayerId = "player-1",
        )

    @BeforeEach
    fun setUp() {
        gameService = mock(GameService::class.java)
        connectionRegistry = mock(ConnectionRegistry::class.java)
        handler = GameWebSocketHandler(gameService, connectionRegistry, testDispatcher)

        session1 = mock(WebSocketSession::class.java)
        whenever(session1.id).thenReturn("session-1")
        whenever(session1.isOpen).thenReturn(true)
        whenever(connectionRegistry.playerSessionFor("session-1")).thenReturn(
            PlayerSession(
                "game-1",
                "player-1",
            ),
        )

        session2 = mock(WebSocketSession::class.java)
        whenever(session2.id).thenReturn("session-2")
        whenever(session2.isOpen).thenReturn(true)
        whenever(connectionRegistry.playerSessionFor("session-2")).thenReturn(
            PlayerSession(
                "game-1",
                "player-2",
            ),
        )

        whenever(
            connectionRegistry.connectionsFor("game-1"),
        ).thenReturn(setOf(session1, session2))
    }

    @Test
    fun `send does not send message to closed session`() =
        runTest(testDispatcher.scheduler) {
            val returnedState =
                GameState(
                    players = listOf(),
                    hostPlayerId = null,
                )

            whenever(connectionRegistry.connectionsFor("game-1")).thenReturn(setOf())

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
    fun `broadcastStateUpdate continues even if one player throws an exception`() =
        runTest(testDispatcher.scheduler) {
            val session3 = mock(WebSocketSession::class.java)
            whenever(session3.id).thenReturn("session-3")

            val returnedState = baseState

            whenever(connectionRegistry.playerSessionFor("session-3")).thenReturn(
                PlayerSession(
                    "game-1",
                    "player-3",
                ),
            )
            whenever(connectionRegistry.connectionsFor("game-1")).thenReturn(
                setOf(
                    session1,
                    session2,
                    session3,
                ),
            )

            // Session 1 is open, but its network stream is broken and throws an error on write
            whenever(session1.isOpen).thenReturn(true)
            whenever(
                session1.sendMessage(any()),
            ).thenThrow(RuntimeException("Network socket broken"))

            // Session 2 is completely healthy
            whenever(session2.isOpen).thenReturn(true)

            // Session 2 is completely healthy and represents the acting player
            whenever(session3.isOpen).thenReturn(true)

            whenever(gameService.leaveGame("game-1", "player-3")).thenReturn(returnedState)

            // Act
            sendEnvelope(
                session = session3,
                type = WebSocketType.LEAVE_GAME,
                requestId = "req-1",
            )

            // Assert: Session 1 crashed, but Session 2 still received
            // the update and session3 received the direct message
            verify(session1).sendMessage(any())
            verify(session2).sendMessage(any())
            verify(session3).sendMessage(any())
        }

    @Test
    fun `broadcastStateUpdate ignores closed sessions`() =
        runTest(testDispatcher.scheduler) {
            val returnedState =
                GameState(
                    players = listOf(Player("player-2", "Player2")),
                    hostPlayerId = "player-2",
                )

            whenever(connectionRegistry.connectionsFor("game-1")).thenReturn(setOf(session2))

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
    fun `handleGameStateChanged broadcasts GAME_STATE_UPDATED`() {
        val gameState = baseState.copy(phase = GamePhase.AUCTION_BIDDING)
        val event = GameStateChangedEvent(gameId = "game-1", newState = gameState)

        handler.handleGameStateChanged(event)

        val response1 = captureResponse(session1)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
        assertNull(response1.requestId)

        val payload1 = decodePayload(response1, GameStatePayload.serializer())

        assertEquals(gameState, payload1.state)
        assertEquals(gameState.createViewForPlayer("player-1"), payload1.stateView)

        val response2 = captureResponse(session2)
        assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
        assertNull(response2.requestId)

        val payload2 = decodePayload(response2, GameStatePayload.serializer())

        assertEquals(gameState, payload2.state)
        assertEquals(gameState.createViewForPlayer("player-2"), payload2.stateView)
    }

    @Test
    fun `handleGameStateChanged skips closed sessions`() {
        val gameState = GameState(phase = GamePhase.AUCTION_BIDDING)
        val event = GameStateChangedEvent(gameId = "game-1", newState = gameState)

        whenever(session1.isOpen).thenReturn(false)
        whenever(connectionRegistry.connectionsFor("game-1")).thenReturn(setOf(session1))

        handler.handleGameStateChanged(event)

        verify(session1, never()).sendMessage(any())
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
        sendEnvelope(
            session1,
            type = WebSocketType.GAME_CREATED,
            requestId = "req-1",
        )

        verifyNoInteractions(gameService, connectionRegistry)
        assertErrorResponse(session1, "req-1", GameErrorReason.UNSUPPORTED_MESSAGE_TYPE.name)
    }

    @Test
    fun `handleTextMessage sends ERROR when GameService throws`() =
        runTest(testDispatcher.scheduler) {
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
    fun `afterConnectionEstablished binds session`() {
        handler.afterConnectionEstablished(session1)

        verify(connectionRegistry).bindConnection(session1)
    }

    @Test
    fun `afterConnectionClosed unbinds session`() {
        handler.afterConnectionClosed(session1, CloseStatus.NORMAL)

        verify(connectionRegistry).unbind("session-1")
    }

    @Test
    fun `afterConnectionClosed removes player and broadcasts GAME_STATE_UPDATED`() =
        runTest(testDispatcher.scheduler) {
            val returnedState =
                GameState(
                    players = listOf(Player("player-2", "Player2")),
                    hostPlayerId = "player-2",
                )

            whenever(connectionRegistry.connectionsFor("game-1")).thenReturn(setOf(session2))

            // Fingerprint unchanged across the grace period, so the player really left.
            whenever(gameService.reconnectTokenFingerprint("game-1", "player-1"))
                .thenReturn("fingerprint-1")
            whenever(gameService.leaveGame("game-1", "player-1")).thenReturn(returnedState)

            handler.afterConnectionClosed(session1, CloseStatus.NORMAL)
            advanceUntilIdle()

            verify(gameService).leaveGame("game-1", "player-1")
            verify(connectionRegistry).unbind("session-1")

            val response2 = captureResponse(session2)
            assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)

            val payload2 = decodePayload(response2, GameStatePayload.serializer())
            assertEquals(returnedState, payload2.state)
            assertEquals(returnedState.createViewForPlayer("player-2"), payload2.stateView)
        }

    @Test
    fun `afterConnectionClosed keeps the player when they reconnected during the grace period`() =
        runTest(testDispatcher.scheduler) {
            // Token rotated during the grace period, so the player reconnected somewhere
            // and must stay in the game.
            whenever(gameService.reconnectTokenFingerprint("game-1", "player-1"))
                .thenReturn("fingerprint-1", "fingerprint-2")

            handler.afterConnectionClosed(session1, CloseStatus.NORMAL)
            advanceUntilIdle()

            verify(connectionRegistry).unbind("session-1")
            verify(gameService, never()).leaveGame(any(), any())
        }

    @Test
    fun `afterConnectionClosed still removes the player when no reconnect token was ever stored`() =
        runTest(testDispatcher.scheduler) {
            // Both fingerprints are null (token never persisted / write failed). The player did
            // NOT reconnect, so the grace period must still end in a leave. Regression guard for
            // the null fingerprint being misread as "already gone".
            whenever(gameService.reconnectTokenFingerprint("game-1", "player-1")).thenReturn(null)
            whenever(gameService.leaveGame("game-1", "player-1")).thenReturn(baseState)

            handler.afterConnectionClosed(session1, CloseStatus.NORMAL)
            advanceUntilIdle()

            verify(gameService).leaveGame("game-1", "player-1")
        }

    @Test
    fun `CREATE_GAME binds session and sends GAME_CREATED`() =
        runTest(testDispatcher.scheduler) {
            val createdSession =
                GameSession(
                    gameId = "game-1",
                    hostPlayerId = "player-1",
                    hostPlayerName = "Player1",
                )

            val returnedResult =
                RoomActionResult(
                    "game-1",
                    "player-1",
                    createdSession.state,
                )

            whenever(connectionRegistry.playerSessionFor("session-1")).thenReturn(null)
            whenever(gameService.createGame("Player1")).thenReturn(returnedResult)

            sendEnvelope(
                session = session1,
                type = WebSocketType.CREATE_GAME,
                requestId = "req-1",
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        CreateGamePayload.serializer(),
                        CreateGamePayload("Player1"),
                    ),
            )

            verify(gameService).createGame("Player1")

            val response = captureResponse(session1)
            assertEquals(WebSocketType.GAME_CREATED, response.type)
            assertEquals("req-1", response.requestId)

            val payload = decodePayload(response, GameCreatedPayload.serializer())

            assertEquals("game-1", payload.gameId)
            assertEquals(createdSession.state, payload.state)
            assertEquals(createdSession.state.createViewForPlayer("player-1"), payload.stateView)
            verify(connectionRegistry).bindPlayerSession("session-1", "game-1", "player-1")
            verify(gameService).storeReconnectToken("game-1", "player-1", payload.reconnectToken)
        }

    @Test
    fun `CREATE_GAME with bound session sends ERROR`() {
        sendEnvelope(
            session = session1,
            type = WebSocketType.CREATE_GAME,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    CreateGamePayload.serializer(),
                    CreateGamePayload("Player1"),
                ),
        )

        verifyNoInteractions(gameService)
        verify(connectionRegistry).playerSessionFor("session-1")
        verify(connectionRegistry, never()).bindPlayerSession(any(), any(), any())

        assertErrorResponse(session1, "req-1", GameErrorReason.CONNECTION_ALREADY_BOUND.name)
    }

    @Test
    fun `JOIN_GAME binds game and player, sends GAME_JOINED, and broadcasts GAME_STATE_UPDATED`() =
        runTest(testDispatcher.scheduler) {
            val state = baseState

            val returnedResult =
                RoomActionResult(
                    "game-1",
                    "player-1",
                    state,
                )

            whenever(connectionRegistry.playerSessionFor("session-1")).thenReturn(null)
            whenever(connectionRegistry.connectionsFor("game-1")).thenReturn(
                setOf(
                    session1,
                    session2,
                ),
            )

            whenever(
                gameService.joinGame("game-1", "Player1"),
            ).thenReturn(returnedResult)

            sendEnvelope(
                session = session1,
                type = WebSocketType.JOIN_GAME,
                requestId = "req-1",
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        JoinGamePayload.serializer(),
                        JoinGamePayload("game-1", "Player1"),
                    ),
            )

            verify(gameService).joinGame("game-1", "Player1")

            val response1 = captureResponse(session1)
            assertEquals(WebSocketType.GAME_JOINED, response1.type)
            assertEquals("req-1", response1.requestId)

            val payload1 = decodePayload(response1, GameJoinedPayload.serializer())

            assertEquals("player-1", payload1.playerId)
            assertEquals(state, payload1.state)
            assertEquals(state.createViewForPlayer("player-1"), payload1.stateView)
            verify(connectionRegistry).bindPlayerSession("session-1", "game-1", "player-1")
            verify(gameService).storeReconnectToken("game-1", "player-1", payload1.reconnectToken)

            val response2 = captureResponse(session2)
            assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
            assertNull(response2.requestId)

            val payload2 = decodePayload(response2, GameStatePayload.serializer())

            assertEquals(state, payload2.state)
            assertEquals(state.createViewForPlayer("player-2"), payload2.stateView)
        }

    @Test
    fun `JOIN_GAME with bound player session sends ERROR`() {
        sendEnvelope(
            session = session1,
            type = WebSocketType.JOIN_GAME,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    JoinGamePayload.serializer(),
                    JoinGamePayload("game-2", "Player1"),
                ),
        )

        verifyNoInteractions(gameService)
        assertErrorResponse(session1, "req-1", GameErrorReason.CONNECTION_ALREADY_BOUND.name)
    }

    @Test
    fun `JOIN_GAME with missing payload sends ERROR`() {
        whenever(connectionRegistry.playerSessionFor("session-1")).thenReturn(null)

        sendEnvelope(session = session1, type = WebSocketType.JOIN_GAME, requestId = "req-1")

        assertErrorResponse(session1, "req-1", GameErrorReason.MISSING_PAYLOAD.name)
    }

    @Test
    fun `JOIN_GAME with invalid payload sends ERROR`() {
        whenever(connectionRegistry.playerSessionFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.JOIN_GAME,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    CreateGamePayload.serializer(),
                    CreateGamePayload("Player1"),
                ),
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.INVALID_PAYLOAD.name)
    }

    @Test
    fun `LEAVE_GAME unbinds session, sends GAME_LEFT, and broadcasts GAME_STATE_UPDATED`() =
        runTest(testDispatcher.scheduler) {
            val returnedState = baseState

            whenever(connectionRegistry.connectionsFor("game-1")).thenReturn(setOf(session2))

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
            assertEquals(returnedState.createViewForPlayer("player-2"), payload2.stateView)
        }

    @Test
    fun `LEAVE_GAME with no bound player session sends ERROR`() {
        whenever(connectionRegistry.playerSessionFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.LEAVE_GAME,
            requestId = "req-1",
        )

        verifyNoInteractions(gameService)
        assertErrorResponse(session1, "req-1", GameErrorReason.CONNECTION_NOT_BOUND.name)
    }

    @Test
    fun `RECONNECT reconnects session and sends SNAPSHOT`() =
        runTest(testDispatcher.scheduler) {
            val returnedState =
                GameState(
                    players = listOf(Player("player-1", "Player1")),
                    hostPlayerId = "player-1",
                )

            whenever(connectionRegistry.playerSessionFor("session-1")).thenReturn(null)
            whenever(gameService.getStateForReconnection("game-1", "player-1"))
                .thenReturn(returnedState)
            whenever(gameService.isReconnectTokenValid("game-1", "player-1", "token-1"))
                .thenReturn(true)

            sendEnvelope(
                session = session1,
                type = WebSocketType.RECONNECT,
                requestId = "req-1",
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        ReconnectPayload.serializer(),
                        ReconnectPayload("game-1", "player-1", "token-1"),
                    ),
            )

            val response = captureResponse(session1)
            assertEquals(WebSocketType.SNAPSHOT, response.type)
            assertEquals("req-1", response.requestId)

            val payload = decodePayload(response, SnapshotPayload.serializer())

            assertEquals(returnedState, payload.state)
            assertEquals(returnedState.createViewForPlayer("player-1"), payload.stateView)
            verify(connectionRegistry).bindPlayerSession("session-1", "game-1", "player-1")
            verify(gameService).storeReconnectToken("game-1", "player-1", payload.reconnectToken)
        }

    @Test
    fun `RECONNECT with bound player session sends ERROR`() {
        sendEnvelope(
            session = session1,
            type = WebSocketType.JOIN_GAME,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    JoinGamePayload.serializer(),
                    JoinGamePayload("game-1", "Player1"),
                ),
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.CONNECTION_ALREADY_BOUND.name)
    }

    @Test
    fun `RECONNECT with missing payload sends ERROR`() {
        whenever(connectionRegistry.playerSessionFor("session-1")).thenReturn(null)

        sendEnvelope(session = session1, type = WebSocketType.RECONNECT, requestId = "req-1")

        assertErrorResponse(session1, "req-1", GameErrorReason.MISSING_PAYLOAD.name)
    }

    @Test
    fun `RECONNECT with invalid payload sends ERROR`() {
        whenever(connectionRegistry.playerSessionFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.RECONNECT,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    CreateGamePayload.serializer(),
                    CreateGamePayload("Player1"),
                ),
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.INVALID_PAYLOAD.name)
    }

    @Test
    fun `RECONNECT with invalid reconnection token sends ERROR`() =
        runTest(testDispatcher.scheduler) {
            val returnedState =
                GameState(
                    players = listOf(Player("player-1", "Player1")),
                    hostPlayerId = "player-1",
                )

            whenever(connectionRegistry.playerSessionFor("session-1")).thenReturn(null)
            whenever(gameService.getStateForReconnection("game-1", "player-1"))
                .thenReturn(returnedState)
            whenever(gameService.isReconnectTokenValid("game-1", "player-1", "invalid-token"))
                .thenReturn(false)

            sendEnvelope(
                session = session1,
                type = WebSocketType.RECONNECT,
                requestId = "req-1",
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        ReconnectPayload.serializer(),
                        ReconnectPayload("game-1", "player-1", "invalid-token"),
                    ),
            )

            assertErrorResponse(session1, "req-1", GameErrorReason.INVALID_RECONNECTION_TOKEN.name)
        }

    @Test
    fun `START_GAME sends and broadcasts GAME_STATE_UPDATED`() =
        runTest(testDispatcher.scheduler) {
            whenever(connectionRegistry.connectionsFor("game-1")).thenReturn(
                setOf(
                    session1,
                    session2,
                ),
            )

            val gameState = baseState.copy(phase = GamePhase.PLAYER_CHOICE)
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
            assertEquals(gameState.createViewForPlayer("player-1"), payload1.stateView)

            val response2 = captureResponse(session2)
            assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
            assertNull(response2.requestId)

            val payload2 = decodePayload(response2, GameStatePayload.serializer())

            assertEquals(gameState, payload2.state)
            assertEquals(gameState.createViewForPlayer("player-2"), payload2.stateView)
        }

    @Test
    fun `START_GAME with no bound player session sends ERROR`() {
        whenever(connectionRegistry.playerSessionFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.START_GAME,
            requestId = "req-1",
        )

        verifyNoInteractions(gameService)
        assertErrorResponse(session1, "req-1", GameErrorReason.CONNECTION_NOT_BOUND.name)
    }

    @Test
    fun `CHOOSE_AUCTION sends and broadcasts GAME_STATE_UPDATED`() =
        runTest(testDispatcher.scheduler) {
            whenever(connectionRegistry.connectionsFor("game-1")).thenReturn(
                setOf(
                    session1,
                    session2,
                ),
            )

            val gameState = baseState.copy(phase = GamePhase.AUCTION_BIDDING)
            whenever(gameService.chooseAuction("game-1", "player-1")).thenReturn(gameState)

            sendEnvelope(
                session1,
                type = WebSocketType.CHOOSE_AUCTION,
                requestId = "req-1",
            )

            verify(gameService).chooseAuction("game-1", "player-1")

            val response1 = captureResponse(session1)
            assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
            assertEquals("req-1", response1.requestId)

            val payload1 = decodePayload(response1, GameStatePayload.serializer())

            assertEquals(gameState, payload1.state)
            assertEquals(gameState.createViewForPlayer("player-1"), payload1.stateView)

            val response2 = captureResponse(session2)
            assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
            assertNull(response2.requestId)

            val payload2 = decodePayload(response2, GameStatePayload.serializer())

            assertEquals(gameState, payload2.state)
            assertEquals(gameState.createViewForPlayer("player-2"), payload2.stateView)
        }

    @Test
    fun `CHOOSE_AUCTION with no bound player session sends ERROR`() {
        whenever(connectionRegistry.playerSessionFor("session-1")).thenReturn(null)

        sendEnvelope(
            session1,
            type = WebSocketType.CHOOSE_AUCTION,
            requestId = "req-1",
        )

        verifyNoInteractions(gameService)
        assertErrorResponse(session1, "req-1", GameErrorReason.CONNECTION_NOT_BOUND.name)
    }

    @Test
    fun `PLACE_BID sends and broadcasts GAME_STATE_UPDATED`() =
        runTest(testDispatcher.scheduler) {
            whenever(connectionRegistry.connectionsFor("game-1")).thenReturn(
                setOf(
                    session1,
                    session2,
                ),
            )

            val gameState = baseState.copy(phase = GamePhase.AUCTION_BIDDING)
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
            assertEquals(gameState.createViewForPlayer("player-1"), payload1.stateView)

            val response2 = captureResponse(session2)
            assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
            assertNull(response2.requestId)

            val payload2 = decodePayload(response2, GameStatePayload.serializer())

            assertEquals(gameState, payload2.state)
            assertEquals(gameState.createViewForPlayer("player-2"), payload2.stateView)
        }

    @Test
    fun `PLACE_BID with no bound player session sends ERROR`() {
        whenever(connectionRegistry.playerSessionFor("session-1")).thenReturn(null)

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

        assertErrorResponse(session1, "req-1", GameErrorReason.CONNECTION_NOT_BOUND.name)
    }

    @Test
    fun `PLACE_BID with missing payload sends ERROR`() {
        sendEnvelope(
            session = session1,
            type = WebSocketType.PLACE_BID,
            requestId = "req-1",
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.MISSING_PAYLOAD.name)
    }

    @Test
    fun `PLACE_BID with invalid payload sends ERROR`() {
        sendEnvelope(
            session = session1,
            type = WebSocketType.PLACE_BID,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    JoinGamePayload.serializer(),
                    JoinGamePayload("game-1", "Player1"),
                ),
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.INVALID_PAYLOAD.name)
    }

    @Test
    fun `RESOLVE_AUCTION sends and broadcasts GAME_STATE_UPDATED`() =
        runTest(testDispatcher.scheduler) {
            whenever(connectionRegistry.connectionsFor("game-1")).thenReturn(
                setOf(
                    session1,
                    session2,
                ),
            )

            val gameState = baseState.copy(phase = GamePhase.PLAYER_CHOICE)
            whenever(gameService.resolveAuction("game-1", "player-1", true)).thenReturn(gameState)

            sendEnvelope(
                session = session1,
                type = WebSocketType.RESOLVE_AUCTION,
                requestId = "req-1",
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        ResolveAuctionPayload.serializer(),
                        ResolveAuctionPayload(buyBack = true),
                    ),
            )

            verify(gameService).resolveAuction("game-1", "player-1", true)

            val response1 = captureResponse(session1)
            assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
            assertEquals("req-1", response1.requestId)

            val payload1 = decodePayload(response1, GameStatePayload.serializer())

            assertEquals(gameState, payload1.state)
            assertEquals(gameState.createViewForPlayer("player-1"), payload1.stateView)

            val response2 = captureResponse(session2)
            assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
            assertNull(response2.requestId)

            val payload2 = decodePayload(response2, GameStatePayload.serializer())

            assertEquals(gameState, payload2.state)
            assertEquals(gameState.createViewForPlayer("player-2"), payload2.stateView)
        }

    @Test
    fun `RESOLVE_AUCTION with no bound player session sends ERROR`() {
        whenever(connectionRegistry.playerSessionFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.RESOLVE_AUCTION,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    ResolveAuctionPayload.serializer(),
                    ResolveAuctionPayload(buyBack = true),
                ),
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.CONNECTION_NOT_BOUND.name)
    }

    @Test
    fun `RESOLVE_AUCTION with missing payload sends ERROR`() {
        sendEnvelope(
            session = session1,
            type = WebSocketType.RESOLVE_AUCTION,
            requestId = "req-1",
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.MISSING_PAYLOAD.name)
    }

    @Test
    fun `RESOLVE_AUCTION with invalid payload sends ERROR`() {
        sendEnvelope(
            session = session1,
            type = WebSocketType.RESOLVE_AUCTION,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    JoinGamePayload.serializer(),
                    JoinGamePayload("game-1", "Player1"),
                ),
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.INVALID_PAYLOAD.name)
    }

    @Test
    fun `SUBMIT_AUCTION_PAYMENT sends and broadcasts GAME_STATE_UPDATED`() =
        runTest(testDispatcher.scheduler) {
            whenever(connectionRegistry.connectionsFor("game-1")).thenReturn(
                setOf(
                    session1,
                    session2,
                ),
            )

            val cardIds = setOf("m1", "m2")
            val gameState = baseState.copy(phase = GamePhase.AUCTION_RESULT)
            whenever(
                gameService.submitAuctionPayment(
                    "game-1",
                    "player-1",
                    cardIds,
                ),
            ).thenReturn(gameState)

            sendEnvelope(
                session = session1,
                type = WebSocketType.SUBMIT_AUCTION_PAYMENT,
                requestId = "req-1",
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        SubmitAuctionPaymentPayload.serializer(),
                        SubmitAuctionPaymentPayload(moneyCardIds = cardIds),
                    ),
            )

            verify(gameService).submitAuctionPayment(
                "game-1",
                "player-1",
                cardIds,
            )

            val response1 = captureResponse(session1)
            assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
            assertEquals("req-1", response1.requestId)

            val payload1 = decodePayload(response1, GameStatePayload.serializer())

            assertEquals(gameState, payload1.state)
            assertEquals(gameState.createViewForPlayer("player-1"), payload1.stateView)

            val response2 = captureResponse(session2)
            assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
            assertNull(response2.requestId)

            val payload2 = decodePayload(response2, GameStatePayload.serializer())

            assertEquals(gameState, payload2.state)
            assertEquals(gameState.createViewForPlayer("player-2"), payload2.stateView)
        }

    @Test
    fun `SUBMIT_AUCTION_PAYMENT with no bound player session sends ERROR`() {
        whenever(connectionRegistry.playerSessionFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.SUBMIT_AUCTION_PAYMENT,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    SubmitAuctionPaymentPayload.serializer(),
                    SubmitAuctionPaymentPayload(moneyCardIds = emptySet()),
                ),
        )

        verifyNoInteractions(gameService)
        assertErrorResponse(session1, "req-1", GameErrorReason.CONNECTION_NOT_BOUND.name)
    }

    @Test
    fun `SUBMIT_AUCTION_PAYMENT with missing payload sends ERROR`() {
        sendEnvelope(
            session = session1,
            type = WebSocketType.SUBMIT_AUCTION_PAYMENT,
            requestId = "req-1",
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.MISSING_PAYLOAD.name)
    }

    @Test
    fun `SUBMIT_AUCTION_PAYMENT with invalid payload sends ERROR`() {
        sendEnvelope(
            session = session1,
            type = WebSocketType.SUBMIT_AUCTION_PAYMENT,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    JoinGamePayload.serializer(),
                    JoinGamePayload("game-1", "Player1"),
                ),
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.INVALID_PAYLOAD.name)
    }

    @Test
    fun `CHOOSE_TRADE sends and broadcasts GAME_STATE_UPDATED`() =
        runTest(testDispatcher.scheduler) {
            whenever(connectionRegistry.connectionsFor("game-1")).thenReturn(
                setOf(
                    session1,
                    session2,
                ),
            )

            val gameState = baseState.copy(phase = GamePhase.TRADE_OFFER)
            whenever(
                gameService.chooseTrade(
                    "game-1",
                    "player-1",
                    "player-2",
                    AnimalType.COW,
                ),
            ).thenReturn(gameState)

            sendEnvelope(
                session = session1,
                type = WebSocketType.CHOOSE_TRADE,
                requestId = "req-1",
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        ChooseTradePayload.serializer(),
                        ChooseTradePayload(
                            challengedPlayerId = "player-2",
                            animalType = AnimalType.COW,
                        ),
                    ),
            )

            verify(gameService).chooseTrade(
                "game-1",
                "player-1",
                "player-2",
                AnimalType.COW,
            )

            val response1 = captureResponse(session1)
            assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
            assertEquals("req-1", response1.requestId)

            val payload1 = decodePayload(response1, GameStatePayload.serializer())

            assertEquals(gameState, payload1.state)
            assertEquals(gameState.createViewForPlayer("player-1"), payload1.stateView)

            val response2 = captureResponse(session2)
            assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
            assertNull(response2.requestId)

            val payload2 = decodePayload(response2, GameStatePayload.serializer())

            assertEquals(gameState, payload2.state)
            assertEquals(gameState.createViewForPlayer("player-2"), payload2.stateView)
        }

    @Test
    fun `CHOOSE_TRADE with no bound player session sends ERROR`() {
        whenever(connectionRegistry.playerSessionFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.CHOOSE_TRADE,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    ChooseTradePayload.serializer(),
                    ChooseTradePayload(
                        challengedPlayerId = "player-2",
                        animalType = AnimalType.COW,
                    ),
                ),
        )

        verifyNoInteractions(gameService)
        assertErrorResponse(session1, "req-1", GameErrorReason.CONNECTION_NOT_BOUND.name)
    }

    @Test
    fun `CHOOSE_TRADE with missing payload sends ERROR`() {
        sendEnvelope(
            session = session1,
            type = WebSocketType.CHOOSE_TRADE,
            requestId = "req-1",
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.MISSING_PAYLOAD.name)
    }

    @Test
    fun `CHOOSE_TRADE with invalid payload sends ERROR`() {
        sendEnvelope(
            session = session1,
            type = WebSocketType.CHOOSE_TRADE,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    JoinGamePayload.serializer(),
                    JoinGamePayload("game-1", "Player1"),
                ),
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.INVALID_PAYLOAD.name)
    }

    @Test
    fun `SUBMIT_TRADE_MONEY sends and broadcasts GAME_STATE_UPDATED`() =
        runTest(testDispatcher.scheduler) {
            whenever(connectionRegistry.connectionsFor("game-1")).thenReturn(
                setOf(
                    session1,
                    session2,
                ),
            )

            val gameState = baseState.copy(phase = GamePhase.TRADE_OFFER)
            whenever(
                gameService.submitTradeMoney(
                    "game-1",
                    "player-1",
                    emptySet(),
                ),
            ).thenReturn(gameState)

            sendEnvelope(
                session = session1,
                type = WebSocketType.SUBMIT_TRADE_MONEY,
                requestId = "req-1",
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        SubmitTradeMoneyPayload.serializer(),
                        SubmitTradeMoneyPayload(
                            moneyCardIds = emptySet(),
                        ),
                    ),
            )

            verify(gameService).submitTradeMoney(
                "game-1",
                "player-1",
                emptySet(),
            )

            val response1 = captureResponse(session1)
            assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
            assertEquals("req-1", response1.requestId)

            val payload1 = decodePayload(response1, GameStatePayload.serializer())

            assertEquals(gameState, payload1.state)
            assertEquals(gameState.createViewForPlayer("player-1"), payload1.stateView)

            val response2 = captureResponse(session2)
            assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
            assertNull(response2.requestId)

            val payload2 = decodePayload(response2, GameStatePayload.serializer())

            assertEquals(gameState, payload2.state)
            assertEquals(gameState.createViewForPlayer("player-2"), payload2.stateView)
        }

    @Test
    fun `SUBMIT_TRADE_MONEY with no bound player session sends ERROR`() {
        whenever(connectionRegistry.playerSessionFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.SUBMIT_TRADE_MONEY,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    SubmitTradeMoneyPayload.serializer(),
                    SubmitTradeMoneyPayload(
                        moneyCardIds = emptySet(),
                    ),
                ),
        )

        verifyNoInteractions(gameService)
        assertErrorResponse(session1, "req-1", GameErrorReason.CONNECTION_NOT_BOUND.name)
    }

    @Test
    fun `SUBMIT_TRADE_MONEY with missing payload sends ERROR`() {
        sendEnvelope(
            session = session1,
            type = WebSocketType.SUBMIT_TRADE_MONEY,
            requestId = "req-1",
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.MISSING_PAYLOAD.name)
    }

    @Test
    fun `SUBMIT_TRADE_MONEY with invalid payload sends ERROR`() {
        sendEnvelope(
            session = session1,
            type = WebSocketType.SUBMIT_TRADE_MONEY,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    JoinGamePayload.serializer(),
                    JoinGamePayload("game-1", "Player1"),
                ),
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.INVALID_PAYLOAD.name)
    }

    @Test
    fun `RESPOND_TO_TRADE sends and broadcasts GAME_STATE_UPDATED`() =
        runTest(testDispatcher.scheduler) {
            whenever(connectionRegistry.connectionsFor("game-1")).thenReturn(
                setOf(
                    session1,
                    session2,
                ),
            )

            val gameState = baseState.copy(phase = GamePhase.TRADE_RESULT)
            whenever(gameService.respondToTrade("game-1", "player-1", emptySet()))
                .thenReturn(gameState)

            sendEnvelope(
                session = session1,
                type = WebSocketType.RESPOND_TO_TRADE,
                requestId = "req-1",
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        RespondToTradePayload.serializer(),
                        RespondToTradePayload(
                            moneyCardIds = emptySet(),
                        ),
                    ),
            )

            verify(gameService).respondToTrade("game-1", "player-1", emptySet())

            val response1 = captureResponse(session1)
            assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
            assertEquals("req-1", response1.requestId)

            val payload1 = decodePayload(response1, GameStatePayload.serializer())

            assertEquals(gameState, payload1.state)
            assertEquals(gameState.createViewForPlayer("player-1"), payload1.stateView)

            val response2 = captureResponse(session2)
            assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
            assertNull(response2.requestId)

            val payload2 = decodePayload(response2, GameStatePayload.serializer())

            assertEquals(gameState, payload2.state)
            assertEquals(gameState.createViewForPlayer("player-2"), payload2.stateView)
        }

    @Test
    fun `RESPOND_TO_TRADE with no bound player session sends ERROR`() {
        whenever(connectionRegistry.playerSessionFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.RESPOND_TO_TRADE,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    RespondToTradePayload.serializer(),
                    RespondToTradePayload(
                        moneyCardIds = emptySet(),
                    ),
                ),
        )

        verifyNoInteractions(gameService)
        assertErrorResponse(session1, "req-1", GameErrorReason.CONNECTION_NOT_BOUND.name)
    }

    @Test
    fun `RESPOND_TO_TRADE with missing payload sends ERROR`() {
        sendEnvelope(
            session = session1,
            type = WebSocketType.RESPOND_TO_TRADE,
            requestId = "req-1",
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.MISSING_PAYLOAD.name)
    }

    @Test
    fun `RESPOND_TO_TRADE with invalid payload sends ERROR`() {
        sendEnvelope(
            session = session1,
            type = WebSocketType.RESPOND_TO_TRADE,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    JoinGamePayload.serializer(),
                    JoinGamePayload("game-1", "Player1"),
                ),
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.INVALID_PAYLOAD.name)
    }

    @Test
    fun `SPY sends and broadcasts GAME_STATE_UPDATED`() =
        runTest(testDispatcher.scheduler) {
            whenever(connectionRegistry.connectionsFor("game-1")).thenReturn(
                setOf(session1, session2),
            )

            val gameState =
                baseState.copy(
                    activeSpies =
                        setOf(
                            SpyAction(
                                "player-1",
                                "player-2",
                                System.currentTimeMillis() + 5000L,
                                emptySet(),
                            ),
                        ),
                )
            whenever(gameService.spy("game-1", "player-1", "player-2"))
                .thenReturn(gameState)

            sendEnvelope(
                session = session1,
                type = WebSocketType.SPY,
                requestId = "req-1",
                payload =
                    WebSocketJson.json.encodeToJsonElement(
                        SpyPayload.serializer(),
                        SpyPayload(targetPlayerId = "player-2"),
                    ),
            )

            verify(gameService).spy("game-1", "player-1", "player-2")

            val response1 = captureResponse(session1)
            assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
            assertEquals("req-1", response1.requestId)

            val payload1 = decodePayload(response1, GameStatePayload.serializer())

            assertEquals(gameState, payload1.state)
            assertEquals(gameState.createViewForPlayer("player-1"), payload1.stateView)

            val response2 = captureResponse(session2)
            assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
            assertNull(response2.requestId)

            val payload2 = decodePayload(response2, GameStatePayload.serializer())

            assertEquals(gameState, payload2.state)
            assertEquals(gameState.createViewForPlayer("player-2"), payload2.stateView)
        }

    @Test
    fun `SPY with no bound player session sends ERROR`() {
        whenever(connectionRegistry.playerSessionFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.SPY,
            requestId = "req-1",
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    SpyPayload.serializer(),
                    SpyPayload(targetPlayerId = "player-2"),
                ),
        )

        verifyNoInteractions(gameService)
        assertErrorResponse(session1, "req-1", GameErrorReason.CONNECTION_NOT_BOUND.name)
    }

    @Test
    fun `SPY with missing payload sends ERROR`() {
        sendEnvelope(
            session = session1,
            type = WebSocketType.SPY,
            requestId = "req-1",
        )

        assertErrorResponse(session1, "req-1", GameErrorReason.MISSING_PAYLOAD.name)
    }

    @Test
    fun `SPY with invalid payload sends ERROR`() {
        sendEnvelope(
            session = session1,
            type = WebSocketType.SPY,
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
    fun `CATCH_SPY sends and broadcasts GAME_STATE_UPDATED`() =
        runTest(testDispatcher.scheduler) {
            whenever(connectionRegistry.connectionsFor("game-1")).thenReturn(
                setOf(session1, session2),
            )

            val gameState = baseState.copy(activeSpies = emptySet())
            whenever(gameService.catchSpy("game-1", "player-1"))
                .thenReturn(gameState)

            sendEnvelope(
                session = session1,
                type = WebSocketType.CATCH_SPY,
                requestId = "req-1",
            )

            verify(gameService).catchSpy("game-1", "player-1")

            val response1 = captureResponse(session1)
            assertEquals(WebSocketType.GAME_STATE_UPDATED, response1.type)
            assertEquals("req-1", response1.requestId)

            val payload1 = decodePayload(response1, GameStatePayload.serializer())

            assertEquals(gameState, payload1.state)
            assertEquals(gameState.createViewForPlayer("player-1"), payload1.stateView)

            val response2 = captureResponse(session2)
            assertEquals(WebSocketType.GAME_STATE_UPDATED, response2.type)
            assertNull(response2.requestId)

            val payload2 = decodePayload(response2, GameStatePayload.serializer())

            assertEquals(gameState, payload2.state)
            assertEquals(gameState.createViewForPlayer("player-2"), payload2.stateView)
        }

    @Test
    fun `CATCH_SPY with no bound player session sends ERROR`() {
        whenever(connectionRegistry.playerSessionFor("session-1")).thenReturn(null)

        sendEnvelope(
            session = session1,
            type = WebSocketType.CATCH_SPY,
            requestId = "req-1",
        )

        verifyNoInteractions(gameService)
        assertErrorResponse(session1, "req-1", GameErrorReason.CONNECTION_NOT_BOUND.name)
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
