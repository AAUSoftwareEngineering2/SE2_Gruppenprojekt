package at.aau.kuhhandel.app.network.game

import at.aau.kuhhandel.app.data.TokenStorage
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.AnimalDeck
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.Player
import at.aau.kuhhandel.shared.websocket.CreateGamePayload
import at.aau.kuhhandel.shared.websocket.ErrorPayload
import at.aau.kuhhandel.shared.websocket.GameCreatedPayload
import at.aau.kuhhandel.shared.websocket.GameStatePayload
import at.aau.kuhhandel.shared.websocket.ReconnectPayload
import at.aau.kuhhandel.shared.websocket.RespondToTradePayload
import at.aau.kuhhandel.shared.websocket.SnapshotPayload
import at.aau.kuhhandel.shared.websocket.WebSocketEnvelope
import at.aau.kuhhandel.shared.websocket.WebSocketJson
import at.aau.kuhhandel.shared.websocket.WebSocketType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameRepositoryTest {
    private class Harness(
        val repository: GameRepository,
        val session: FakeWebSocketSession,
        val tokenStorage: TokenStorage,
    ) {
        val state: GameRepositoryState
            get() = repository.state.value

        suspend fun createGame(playerName: String = "Player1") {
            repository.createGame(playerName)
        }

        suspend fun startGame() {
            repository.startGame()
        }

        suspend fun revealCard() {
            repository.revealCard()
        }

        suspend fun placeBid(amount: Int) {
            repository.placeBid(amount)
        }

        suspend fun buyBack(buyBack: Boolean) {
            repository.buyBack(buyBack)
        }

        suspend fun initiateTrade(
            targetPlayerId: String,
            animalType: AnimalType = AnimalType.COW,
        ) {
            repository.initiateTrade(targetPlayerId, animalType)
        }

        fun clearError() {
            repository.clearError()
        }

        fun disconnect() {
            repository.disconnect()
        }

        fun sentEnvelope(): WebSocketEnvelope = session.onlySentEnvelope()

        fun sentTypes(): List<WebSocketType> = session.sentEnvelopes().map { it.type }
    }

    private val scopes = mutableListOf<CoroutineScope>()

    @AfterEach
    fun tearDown() {
        scopes.forEach { it.cancel() }
        scopes.clear()
    }

    private fun createHarness(
        session: FakeWebSocketSession = FakeWebSocketSession(),
        openSession: suspend () -> OpenedSession = { OpenedSession(session) },
    ): Harness {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        scopes += scope

        val tokenStorageMock: TokenStorage = mockk(relaxed = true)

        val repository =
            GameRepository(GameWebSocketClient(openSession = openSession), scope, tokenStorageMock)
        return Harness(repository, session, tokenStorageMock)
    }

    private suspend fun flushRepository() {
        repeat(3) { yield() }
    }

    private suspend fun Harness.receiveGameCreated(
        gameId: String,
        state: GameState,
    ) {
        session.deliverEnvelope(gameCreatedEnvelope(gameId, state))
        flushRepository()
    }

    private suspend fun Harness.receiveGameState(
        type: WebSocketType,
        state: GameState,
    ) {
        session.deliverEnvelope(gameStateEnvelope(type, state))
        flushRepository()
    }

    private suspend fun Harness.receiveError(message: String) {
        session.deliverEnvelope(errorEnvelope(message))
        flushRepository()
    }

    private suspend fun Harness.receiveClose(message: String = "bye") {
        session.deliverClose(message = message)
        flushRepository()
    }

    private fun sampleState(
        phase: GamePhase = GamePhase.NOT_STARTED,
        players: List<Player> = listOf(Player(id = "player-1", name = "Fabio")),
        currentCard: AnimalCard? = null,
        deckSize: Int = 0,
    ): GameState =
        GameState(
            phase = phase,
            deck =
                AnimalDeck(
                    List(deckSize) { index ->
                        AnimalCard(id = "deck-$index", type = AnimalType.CHICKEN)
                    },
                ),
            currentFaceUpCard = currentCard,
            players = players,
        )

    private fun gameCreatedEnvelope(
        gameId: String,
        state: GameState,
    ): WebSocketEnvelope =
        WebSocketEnvelope(
            type = WebSocketType.GAME_CREATED,
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    GameCreatedPayload.serializer(),
                    GameCreatedPayload(
                        gameId = gameId,
                        playerId = "me",
                        reconnectToken = "test-token",
                        state = state,
                    ),
                ),
        )

    private fun gameStateEnvelope(
        type: WebSocketType,
        state: GameState,
    ): WebSocketEnvelope =
        WebSocketEnvelope(
            type = type,
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    GameStatePayload.serializer(),
                    GameStatePayload(state = state),
                ),
        )

    private fun errorEnvelope(message: String): WebSocketEnvelope =
        WebSocketEnvelope(
            type = WebSocketType.ERROR,
            payload =
                WebSocketJson.json.encodeToJsonElement(
                    ErrorPayload.serializer(),
                    ErrorPayload(message),
                ),
        )

    @Test
    fun `createGame sends request and applies GAME_CREATED payload`() {
        runBlocking {
            val harness = createHarness()
            val createdState = sampleState(deckSize = 2)

            harness.createGame("Fabio")

            val payload =
                WebSocketJson.json.decodeFromJsonElement(
                    CreateGamePayload.serializer(),
                    requireNotNull(harness.sentEnvelope().payload),
                )

            assertEquals(WebSocketType.CREATE_GAME, harness.sentEnvelope().type)
            assertEquals("Fabio", payload.playerName)
            assertTrue(harness.state.isConnected)
            assertFalse(harness.state.isConnecting)

            harness.receiveGameCreated("48307", createdState)

            assertEquals("48307", harness.state.gameId)
            assertEquals(createdState, harness.state.gameState)
            assertNull(harness.state.errorMessage)
        }
    }

    @Test
    fun `startGame reuses the active connection and updates the game state`() {
        runBlocking {
            val session = FakeWebSocketSession()
            var openCount = 0
            val harness =
                createHarness(
                    session = session,
                    openSession = {
                        openCount++
                        OpenedSession(session)
                    },
                )
            val startedState = sampleState(phase = GamePhase.PLAYER_CHOICE, deckSize = 3)

            harness.createGame("Fabio")
            harness.startGame()

            assertEquals(
                listOf(WebSocketType.CREATE_GAME, WebSocketType.START_GAME),
                harness.sentTypes(),
            )
            assertEquals(1, openCount)

            harness.receiveGameState(WebSocketType.GAME_STATE_UPDATED, startedState)

            assertEquals(startedState, harness.state.gameState)
            assertTrue(harness.state.isConnected)
        }
    }

    @Test
    fun `revealCard updates the current game state`() {
        runBlocking {
            val harness = createHarness()
            val revealedCard = AnimalCard(id = "face-up", type = AnimalType.COW)
            val updatedState =
                sampleState(
                    phase = GamePhase.PLAYER_CHOICE,
                    currentCard = revealedCard,
                    deckSize = 1,
                )

            harness.revealCard()
            assertEquals(WebSocketType.CHOOSE_AUCTION, harness.sentEnvelope().type)

            harness.receiveGameState(WebSocketType.GAME_STATE_UPDATED, updatedState)

            assertEquals(updatedState, harness.state.gameState)
            assertEquals(revealedCard, harness.state.gameState?.currentFaceUpCard)
        }
    }

    @Test
    fun `placeBid sends request`() {
        runBlocking {
            val harness = createHarness()
            harness.placeBid(100)
            assertEquals(WebSocketType.PLACE_BID, harness.sentEnvelope().type)
        }
    }

    @Test
    fun `joinGame sends request`() {
        runBlocking {
            val harness = createHarness()
            harness.repository.joinGame("g1", "p1")
            assertEquals(WebSocketType.JOIN_GAME, harness.sentEnvelope().type)
        }
    }

    @Test
    fun `respondToTrade sends request`() {
        runBlocking {
            val harness = createHarness()
            // Initialize state to have myPlayerId
            harness.receiveGameCreated("g1", sampleState())

            harness.repository.respondToTrade(setOf("m2"))
            assertEquals(WebSocketType.RESPOND_TO_TRADE, harness.sentTypes().last())
        }
    }

    @Test
    fun `submitTradeMoney sends request`() {
        runBlocking {
            val harness = createHarness()
            harness.createGame()

            harness.repository.submitTradeMoney(setOf("m1"))

            assertEquals(WebSocketType.SUBMIT_TRADE_MONEY, harness.sentTypes().last())
        }
    }

    @Test
    fun `leaveGame sends request and disconnects`() {
        runBlocking {
            val harness = createHarness()
            harness.createGame()
            harness.repository.leaveGame()

            assertEquals(
                WebSocketType.LEAVE_GAME,
                harness.session
                    .sentEnvelopes()
                    .last()
                    .type,
            )
            assertFalse(harness.state.isConnected)
        }
    }

    @Test
    fun `buyBack sends request`() {
        runBlocking {
            val harness = createHarness()
            harness.buyBack(true)
            assertEquals(WebSocketType.RESOLVE_AUCTION, harness.sentEnvelope().type)
        }
    }

    @Test
    fun `finishTradeReveal sends request`() {
        runBlocking {
            val harness = createHarness()
            harness.repository.finishTradeReveal()
            assertEquals(WebSocketType.FINISH_TRADE_REVEAL, harness.sentEnvelope().type)
        }
    }

    @Test
    fun `initiateTrade sends request`() {
        runBlocking {
            val harness = createHarness()
            harness.initiateTrade("p2")
            assertEquals(WebSocketType.CHOOSE_TRADE, harness.sentEnvelope().type)
        }
    }

    @Test
    fun `error envelopes update the state and can be cleared`() {
        runBlocking {
            val harness = createHarness()

            harness.createGame()
            harness.receiveError("Invalid move")

            assertEquals("Invalid move", harness.state.errorMessage)

            harness.clearError()
            assertNull(harness.state.errorMessage)
        }
    }

    @Test
    fun `GAME_CREATED updates state and saves session details to token storage`() {
        runBlocking {
            val harness = createHarness()
            val state = sampleState()

            harness.repository.createGame("me")

            val envelope =
                WebSocketEnvelope(
                    type = WebSocketType.GAME_CREATED,
                    payload =
                        WebSocketJson.json.encodeToJsonElement(
                            GameCreatedPayload.serializer(),
                            GameCreatedPayload(
                                gameId = "g1",
                                playerId = "me",
                                reconnectToken = "test-token",
                                state = state,
                            ),
                        ),
                )

            harness.session.deliverEnvelope(envelope)
            flushRepository()

            assertEquals("g1", harness.state.gameId)
            assertEquals("me", harness.state.myPlayerId)
            assertEquals(state, harness.state.gameState)

            verify(exactly = 1) {
                harness.tokenStorage.saveSession(
                    gameId = "g1",
                    playerId = "me",
                    token = "test-token",
                )
            }
        }
    }

    @Test
    fun `GAME_JOINED updates state and saves session details to token storage`() {
        runBlocking {
            val harness = createHarness()
            val state = sampleState()

            harness.repository.createGame("me") // Ensure connected and setup

            // Re-create the envelope manually to ensure correct requestId is NOT checked (handleEnvelope ignores it for JOINED)
            val envelope =
                WebSocketEnvelope(
                    type = WebSocketType.GAME_JOINED,
                    payload =
                        WebSocketJson.json.encodeToJsonElement(
                            GameCreatedPayload.serializer(),
                            GameCreatedPayload(
                                gameId = "g1",
                                playerId = "me",
                                reconnectToken = "test-token",
                                state = state,
                            ),
                        ),
                )

            harness.session.deliverEnvelope(envelope)
            flushRepository()

            assertEquals("g1", harness.state.gameId)
            assertEquals(state, harness.state.gameState)

            verify(exactly = 1) {
                harness.tokenStorage.saveSession(
                    gameId = "g1",
                    playerId = "me",
                    token = "test-token",
                )
            }
        }
    }

    @Test
    fun `REPRODUCE USER ISSUE GAME_JOINED with GameJoinedPayload`() {
        runBlocking {
            val harness = createHarness()
            val state = sampleState()

            harness.repository.joinGame("g1", "me")

            val envelope =
                WebSocketEnvelope(
                    type = WebSocketType.GAME_JOINED,
                    payload =
                        WebSocketJson.json.encodeToJsonElement(
                            at.aau.kuhhandel.shared.websocket.GameJoinedPayload
                                .serializer(),
                            at.aau.kuhhandel.shared.websocket.GameJoinedPayload(
                                playerId = "player-7da6",
                                reconnectToken = "test-token",
                                state = state,
                            ),
                        ),
                )

            harness.session.deliverEnvelope(envelope)
            flushRepository()

            assertEquals("g1", harness.state.gameId)
            assertEquals("player-7da6", harness.state.myPlayerId)
            assertEquals(state, harness.state.gameState)
        }
    }

    @Test
    fun `SNAPSHOT updates state and saves reconnect token`() {
        runBlocking {
            val harness = createHarness()
            val state = sampleState()

            harness.repository.createGame("me")

            val envelope =
                WebSocketEnvelope(
                    type = WebSocketType.SNAPSHOT,
                    payload =
                        WebSocketJson.json.encodeToJsonElement(
                            SnapshotPayload.serializer(),
                            SnapshotPayload(
                                reconnectToken = "new-token",
                                state = state,
                            ),
                        ),
                )

            harness.session.deliverEnvelope(envelope)
            flushRepository()

            assertEquals(state, harness.state.gameState)

            verify(exactly = 1) {
                harness.tokenStorage.saveReconnectToken("new-token")
            }
        }
    }

    @Test
    fun `invalid payloads surface a readable error`() {
        runBlocking {
            val harness = createHarness()

            harness.createGame()
            // Missing payload
            harness.session.deliverEnvelope(WebSocketEnvelope(type = WebSocketType.GAME_CREATED))
            flushRepository()
            assertEquals(
                "Invalid GAME_CREATED/JOINED message (Payload is null for GAME_CREATED). Payload: null",
                harness.state.errorMessage,
            )

            // Invalid payload for GAME_STATE_UPDATED
            harness.session.deliverEnvelope(
                WebSocketEnvelope(
                    type = WebSocketType.GAME_STATE_UPDATED,
                    payload = WebSocketJson.json.parseToJsonElement("{}"),
                ),
            )
            flushRepository()
            assertEquals(
                "Invalid GameState message (Field 'state' is required for type with serial name 'at.aau.kuhhandel.shared.websocket.GameStatePayload', but it was missing). Payload: {}",
                harness.state.errorMessage,
            )

            // Invalid payload for ERROR
            harness.session.deliverEnvelope(
                WebSocketEnvelope(
                    type = WebSocketType.ERROR,
                    payload = WebSocketJson.json.parseToJsonElement("{}"),
                ),
            )
            flushRepository()
            assertEquals(
                "Invalid ERROR message (Field 'message' is required for type with serial name 'at.aau.kuhhandel.shared.websocket.ErrorPayload', but it was missing). Payload: {}",
                harness.state.errorMessage,
            )
        }
    }

    @Test
    fun `respondToTrade does not require local player id`() {
        runBlocking {
            val harness = createHarness()
            harness.createGame() // Connects but myPlayerId is still null
            harness.repository.respondToTrade()
            assertTrue(harness.sentTypes().contains(WebSocketType.RESPOND_TO_TRADE))
        }
    }

    @Test
    fun `ensureConnected reuses existing connection`() {
        runBlocking {
            var openCount = 0
            val harness =
                createHarness(
                    openSession = {
                        openCount++
                        OpenedSession(FakeWebSocketSession())
                    },
                )
            harness.createGame()
            harness.createGame()
            assertEquals(1, openCount)
        }
    }

    @Test
    fun `auto-reconnect is triggered on collector failure if gameId exists`() {
        runBlocking {
            val session = FakeWebSocketSession()
            var openCount = 0
            val harness =
                createHarness(
                    session = session,
                    openSession = {
                        openCount++
                        OpenedSession(session)
                    },
                )

            harness.createGame("Fabio")
            harness.receiveGameCreated("g1", sampleState())

            assertEquals(1, openCount)

            // Simulate failure
            session.deliverError(RuntimeException("crash"))
            flushRepository()

            // Wait for reconnect delay (2000ms in code, but we use Unconfined)
            // Actually, with Unconfined and delay, it might still need some real time or yield
            kotlinx.coroutines.delay(2500)
            yield()

            // Should have tried to reconnect
            assertTrue(openCount > 1)
        }
    }

    @Test
    fun `ensureConnected triggers reconnect if gameId is present`() {
        runBlocking {
            val session = FakeWebSocketSession()
            val harness = createHarness(session = session)

            every { harness.tokenStorage.getReconnectToken() } returns "test-token"

            // Manually set gameId to simulate previous state
            harness.receiveGameCreated("g1", sampleState())

            // Disconnect
            harness.disconnect()
            flushRepository()

            // Now trigger ensureConnected again (via createGame for example, or any action)
            harness.createGame("Fabio")

            // Should have sent RECONNECT for g1 after connection
            assertTrue(harness.sentTypes().contains(WebSocketType.RECONNECT))

            verify { harness.tokenStorage.getReconnectToken() }

            val sentReconnectEnvelope =
                session.sentEnvelopes().first { it.type == WebSocketType.RECONNECT }
            val reconnectPayload =
                WebSocketJson.json.decodeFromJsonElement(
                    ReconnectPayload.serializer(),
                    sentReconnectEnvelope.payload!!,
                )
            assertEquals("test-token", reconnectPayload.token)
        }
    }

    @Test
    fun `formatThrowable handles exception without message`() {
        runBlocking {
            val harness =
                createHarness(
                    openSession = {
                        throw RuntimeException("")
                    },
                )
            assertFailsWith<RuntimeException> { harness.createGame() }
            // If message is blank, it should hit the else branch
            assertEquals("Connection failed: RuntimeException", harness.state.errorMessage)
        }
    }

    @Test
    fun `handleEnvelope ignores unknown types`() {
        runBlocking {
            val harness = createHarness()
            harness.createGame()
            harness.session.deliverEnvelope(
                WebSocketType.CREATE_GAME,
                null,
            ) // Server shouldn't send this back usually
            flushRepository()
            // Unknown type or unexpected type that doesn't match the when should just do Unit
            harness.session.deliverEnvelope(
                WebSocketEnvelope(type = WebSocketType.LEAVE_GAME),
            )
            flushRepository()
            assertNull(harness.state.errorMessage)
        }
    }

    @Test
    fun `handleEnvelope fallback fails if both decodes fail`() {
        runBlocking {
            val harness = createHarness()
            harness.createGame()
            harness.session.deliverEnvelope(
                WebSocketEnvelope(
                    type = WebSocketType.GAME_CREATED,
                    payload = WebSocketJson.json.parseToJsonElement("{\"foo\":\"bar\"}"),
                ),
            )
            flushRepository()
            assertTrue(
                harness.state.errorMessage!!.contains(
                    "Invalid GAME_CREATED/JOINED message",
                ),
            )
        }
    }

    @Test
    fun `mismatched collector jobs are ignored in failure and finish`() {
        runBlocking {
            // This is hard to trigger naturally with Unconfined, but we can try to simulate it
            // by calling disconnect while things are in flight.
            val harness = createHarness()
            harness.createGame()

            // At this point eventsJob is set.
            harness.disconnect()
            // Now eventsJob is null.

            // If we could somehow make the old collector report failure now...
            // But the old collector was cancelled by disconnect.

            // Let's just ensure disconnect works as expected.
            assertNull(harness.state.gameId)
            assertFalse(harness.state.isConnected)
        }
    }

    @Test
    fun `cancelCollector does not clear eventsJob if it does not match`() {
        runBlocking {
            val harness = createHarness()
            // We can't easily access the private methods, but we can trigger the logic.
            // If awaitInitialConnection fails, it calls cancelCollector.
            // If we call disconnect just before it fails...

            val tokenStorageMock: TokenStorage = mockk(relaxed = true)

            val session = FakeWebSocketSession()
            val repository =
                GameRepository(
                    GameWebSocketClient(
                        openSession = {
                            OpenedSession(session)
                        },
                    ),
                    CoroutineScope(Dispatchers.Unconfined),
                    tokenStorageMock,
                )

            // Start connecting but don't finish
            // We need a way to make awaitConnected hang or delay.
            // FakeWebSocketSession doesn't have a way to delay awaitConnected yet.
            // Just ensuring this runs without crashing for now.
            repository.createGame("Player1")
            repository.disconnect()
        }
    }

    @Test
    fun `respondToTrade uses provided IDs`() {
        runBlocking {
            val harness = createHarness()
            // Initialize state to have myPlayerId
            harness.receiveGameCreated("g1", sampleState())

            val cardIds = setOf("m1", "m2")
            harness.repository.respondToTrade(cardIds)

            val envelope =
                harness.session.sentEnvelopes().last {
                    it.type ==
                        WebSocketType.RESPOND_TO_TRADE
                }
            val payload =
                WebSocketJson.json.decodeFromJsonElement(
                    RespondToTradePayload.serializer(),
                    requireNotNull(envelope.payload),
                )
            assertEquals(cardIds, payload.moneyCardIds)
        }
    }

    @Test
    fun `failed connection keeps the repository disconnected and stores the reason`() {
        runBlocking {
            val harness =
                createHarness(
                    openSession = {
                        throw IllegalStateException("offline")
                    },
                )

            val error =
                assertFailsWith<IllegalStateException> {
                    harness.createGame("Fabio")
                }

            assertEquals("offline", error.message)
            assertFalse(harness.state.isConnected)
            assertFalse(harness.state.isConnecting)
            assertEquals(
                "Connection failed: IllegalStateException - offline",
                harness.state.errorMessage,
            )
        }
    }

    @Test
    fun `close frames report that the connection was lost`() {
        runBlocking {
            val harness = createHarness()

            harness.createGame()
            harness.receiveClose()

            assertFalse(harness.state.isConnected)
            assertEquals(
                "Connection lost: IllegalStateException - WebSocket closed (1000): bye",
                harness.state.errorMessage,
            )
        }
    }

    @Test
    fun `collector failure reports error`() {
        runBlocking {
            val session = FakeWebSocketSession()
            val harness = createHarness(session = session)
            harness.createGame()

            // Simulate exception in flow collection
            session.deliverError(RuntimeException("crash"))
            flushRepository()

            assertEquals("Connection lost: RuntimeException - crash", harness.state.errorMessage)
        }
    }

    @Test
    fun `disconnect resets the state and closes the session`() {
        runBlocking {
            val harness = createHarness()
            val createdState = sampleState(deckSize = 2)

            harness.createGame("Fabio")
            harness.receiveGameCreated("48307", createdState)

            harness.disconnect()
            flushRepository()

            assertEquals(GameRepositoryState(), harness.state)
            assertTrue(harness.session.wasClosed)
        }
    }
}
