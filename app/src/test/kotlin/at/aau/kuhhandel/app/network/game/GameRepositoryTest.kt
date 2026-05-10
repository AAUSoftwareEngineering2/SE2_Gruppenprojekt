package at.aau.kuhhandel.app.network.game

import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.AnimalDeck
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.PlayerState
import at.aau.kuhhandel.shared.websocket.CreateGamePayload
import at.aau.kuhhandel.shared.websocket.ErrorPayload
import at.aau.kuhhandel.shared.websocket.GameCreatedPayload
import at.aau.kuhhandel.shared.websocket.GameStatePayload
import at.aau.kuhhandel.shared.websocket.WebSocketEnvelope
import at.aau.kuhhandel.shared.websocket.WebSocketJson
import at.aau.kuhhandel.shared.websocket.WebSocketType
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
    ) {
        val state: GameRepositoryState
            get() = repository.state.value

        suspend fun createGame(playerName: String? = null) {
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

        suspend fun initiateTrade(targetPlayerId: String) {
            repository.initiateTrade(targetPlayerId)
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
        val repository = GameRepository(GameWebSocketClient(openSession = openSession), scope)
        return Harness(repository, session)
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
        players: List<PlayerState> = listOf(PlayerState(id = "player-1", name = "Fabio")),
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
                    GameCreatedPayload(gameId = gameId, state = state),
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
            val startedState = sampleState(phase = GamePhase.PLAYER_TURN, deckSize = 3)

            harness.createGame("Fabio")
            harness.startGame()

            assertEquals(
                listOf(WebSocketType.CREATE_GAME, WebSocketType.START_GAME),
                harness.sentTypes(),
            )
            assertEquals(1, openCount)

            harness.receiveGameState(WebSocketType.GAME_STARTED, startedState)

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
                    phase = GamePhase.PLAYER_TURN,
                    currentCard = revealedCard,
                    deckSize = 1,
                )

            harness.revealCard()
            assertEquals(WebSocketType.REVEAL_CARD, harness.sentEnvelope().type)

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
    fun `buyBack sends request`() {
        runBlocking {
            val harness = createHarness()
            harness.buyBack(true)
            assertEquals(WebSocketType.AUCTION_BUY_BACK, harness.sentEnvelope().type)
        }
    }

    @Test
    fun `initiateTrade sends request`() {
        runBlocking {
            val harness = createHarness()
            harness.initiateTrade("p2")
            assertEquals(WebSocketType.INITIATE_TRADE, harness.sentEnvelope().type)
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
    fun `invalid payloads surface a readable error`() {
        runBlocking {
            val harness = createHarness()

            harness.createGame()
            harness.session.deliverEnvelope(WebSocketEnvelope(type = WebSocketType.GAME_CREATED))
            flushRepository()

            assertEquals("Invalid GAME_CREATED message", harness.state.errorMessage)
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
