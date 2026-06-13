package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.event.GameStateChangedEvent
import at.aau.kuhhandel.server.exception.GameException
import at.aau.kuhhandel.server.persistence.GamePersistenceService
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GameErrorReason
import at.aau.kuhhandel.shared.enums.GamePhase
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for the stateless [GameService], running against the real persistence layer
 * (embedded H2) and real [GameSession] logic.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(GamePersistenceService::class)
// No test transaction: the service commits its row-locked transactions on Dispatchers.IO
// threads anyway, so we clean up explicitly instead.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GameServiceTest
    @Autowired
    constructor(
        private val persistenceService: GamePersistenceService,
    ) {
        private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        private val usedGameIds = mutableListOf<String>()

        private fun service(codes: List<String> = emptyList()): GameService {
            val queue = ArrayDeque(codes)
            usedGameIds += codes
            return if (codes.isEmpty()) {
                GameService(eventPublisher, persistenceService)
            } else {
                GameService(
                    eventPublisher,
                    persistenceService,
                    gameCodeGenerator = { queue.removeFirst() },
                )
            }
        }

        @AfterEach
        fun cleanUp() {
            usedGameIds.forEach { runCatching { persistenceService.deleteGame(it) } }
            usedGameIds.clear()
        }

        @Test
        fun `createGame generates a five digit code and persists the lobby`() {
            val service = service()

            val result = service.createGame("Player 1")
            usedGameIds += result.gameId

            assertEquals(5, result.gameId.length)
            assertEquals(GamePhase.NOT_STARTED, result.gameState.phase)
            assertEquals(
                "Player 1",
                result.gameState.players
                    .single()
                    .name,
            )
            assertEquals(
                result.playerId,
                result.gameState.players
                    .single()
                    .id,
            )
            // The lobby snapshot lands in the database immediately.
            assertNotNull(persistenceService.loadGameState(result.gameId))
        }

        @Test
        fun `createGame generates different codes`() {
            val service = service()

            val first = service.createGame("Player 1")
            val second = service.createGame("Player 1")
            usedGameIds += listOf(first.gameId, second.gameId)

            assertNotEquals(first.gameId, second.gameId)
        }

        @Test
        fun `getGame loads the persisted state and returns null for unknown ids`() {
            val service = service(codes = listOf("11111"))
            service.createGame("Player 1")

            val loaded = assertNotNull(service.getGame("11111"))
            assertEquals("11111", loaded.gameId)
            assertEquals(GamePhase.NOT_STARTED, loaded.state.phase)

            assertNull(service.getGame("99999"))
            assertNull(service.getGame("not numeric"))
        }

        @Test
        fun `joinGame adds the player and persists the change`() =
            runTest {
                val service = service(codes = listOf("11111"))
                service.createGame("Player 1")

                val joinResult = service.joinGame("11111", "Player 2")

                assertEquals("11111", joinResult.gameId)
                assertEquals(2, joinResult.gameState.players.size)
                assertEquals(
                    listOf("Player 1", "Player 2"),
                    persistenceService.loadGameState("11111")?.players?.map { it.name },
                )
            }

        @Test
        fun `joinGame throws GAME_NOT_FOUND for unknown games`() =
            runTest {
                val service = service()

                val exception =
                    assertThrows<GameException> { service.joinGame("99999", "Player 2") }
                assertEquals(GameErrorReason.GAME_NOT_FOUND, exception.reason)
            }

        @Test
        fun `leaveGame removes the player and purges an empty game`() =
            runTest {
                val service = service(codes = listOf("11111"))
                val created = service.createGame("Player 1")
                val joined = service.joinGame("11111", "Player 2")

                val afterLeave = service.leaveGame("11111", joined.playerId)
                assertEquals(listOf(created.playerId), afterLeave.players.map { it.id })

                service.leaveGame("11111", created.playerId)
                // Last player gone -> game fully purged from the database.
                assertNull(persistenceService.loadGameState("11111"))
            }

        @Test
        fun `leaveGame throws GAME_NOT_FOUND for unknown games`() =
            runTest {
                val service = service()

                val exception =
                    assertThrows<GameException> { service.leaveGame("99999", "player-1") }
                assertEquals(GameErrorReason.GAME_NOT_FOUND, exception.reason)
            }

        @Test
        fun `getStateForReconnection returns state for a known player`() =
            runTest {
                val service = service(codes = listOf("11111"))
                val created = service.createGame("Player 1")

                val state = service.getStateForReconnection("11111", created.playerId)

                assertEquals(GamePhase.NOT_STARTED, state.phase)
            }

        @Test
        fun `getStateForReconnection throws for unknown game and unknown player`() =
            runTest {
                val service = service(codes = listOf("11111"))
                service.createGame("Player 1")

                val unknownGame =
                    assertThrows<GameException> {
                        service.getStateForReconnection("99999", "player-1")
                    }
                assertEquals(GameErrorReason.GAME_NOT_FOUND, unknownGame.reason)

                val unknownPlayer =
                    assertThrows<GameException> {
                        service.getStateForReconnection("11111", "stranger")
                    }
                assertEquals(GameErrorReason.PLAYER_NOT_IN_GAME, unknownPlayer.reason)
            }

        @Test
        fun `startGame runs the real state machine against persisted state`() =
            runTest {
                val service = service(codes = listOf("11111"))
                val created = service.createGame("Player 1")
                service.joinGame("11111", "Player 2")
                service.joinGame("11111", "Player 3")

                val started = service.startGame("11111", created.playerId)

                assertEquals(GamePhase.PLAYER_CHOICE, started.phase)
                assertNotNull(started.timerEnd)
                // The started state is now in the database.
                assertEquals(
                    GamePhase.PLAYER_CHOICE,
                    persistenceService.loadGameState("11111")?.phase,
                )
            }

        @Test
        fun `game rule violations roll back and leave the persisted state untouched`() =
            runTest {
                val service = service(codes = listOf("11111"))
                val created = service.createGame("Player 1")

                // Two players are not enough, the game needs three.
                assertThrows<GameException> { service.startGame("11111", created.playerId) }

                assertEquals(
                    GamePhase.NOT_STARTED,
                    persistenceService.loadGameState("11111")?.phase,
                )
            }

        @Test
        fun `actions on unknown games throw GAME_NOT_FOUND`() =
            runTest {
                val service = service()

                listOf<suspend () -> Unit>(
                    { service.startGame("99999", "p") },
                    { service.chooseAuction("99999", "p") },
                    { service.placeBid("99999", "p", 10) },
                    { service.resolveAuction("99999", "p", auctioneerBuysCard = false) },
                    { service.submitAuctionPayment("99999", "p", emptySet()) },
                    { service.chooseTrade("99999", "p", "q", AnimalType.COW) },
                    { service.submitTradeMoney("99999", "p", emptySet()) },
                    { service.respondToTrade("99999", "p", emptySet()) },
                ).forEach { action ->
                    val exception = assertThrows<GameException> { action() }
                    assertEquals(GameErrorReason.GAME_NOT_FOUND, exception.reason)
                }
            }

        @Test
        fun `reconnect tokens are stored hashed and validated from the database`() =
            runTest {
                val service = service(codes = listOf("11111"))
                val created = service.createGame("Player 1")

                service.storeReconnectToken("11111", created.playerId, "token-1")

                assertTrue(service.isReconnectTokenValid("11111", created.playerId, "token-1"))
                assertEquals(
                    false,
                    service.isReconnectTokenValid("11111", created.playerId, "wrong"),
                )

                // Token rotation invalidates the previous token.
                service.storeReconnectToken("11111", created.playerId, "token-2")
                assertEquals(
                    false,
                    service.isReconnectTokenValid("11111", created.playerId, "token-1"),
                )
                assertTrue(service.isReconnectTokenValid("11111", created.playerId, "token-2"))
            }

        @Test
        fun `sweepExpiredTimeouts advances an expired phase and publishes the update`() =
            runTest {
                val service = service(codes = listOf("11111"))
                val created = service.createGame("Player 1")
                service.joinGame("11111", "Player 2")
                service.joinGame("11111", "Player 3")
                service.startGame("11111", created.playerId)

                val expired = assertNotNull(persistenceService.loadGameState("11111"))
                val deadline = assertNotNull(expired.timerEnd)

                // Sweep with a "now" after the deadline.
                val advanced = service.sweepExpiredTimeouts(now = deadline + 1)

                assertEquals(listOf("11111"), advanced)
                verify(exactly = 1) { eventPublisher.publishEvent(any<GameStateChangedEvent>()) }

                // Second sweep at the same instant must be a no-op (the new deadline is in
                // the future).
                val secondSweep = service.sweepExpiredTimeouts(now = deadline + 1)
                assertEquals(emptyList(), secondSweep)
            }

        @Test
        fun `sweepExpiredTimeouts ignores games whose timer is still running`() =
            runTest {
                val service = service(codes = listOf("11111"))
                val created = service.createGame("Player 1")
                service.joinGame("11111", "Player 2")
                service.joinGame("11111", "Player 3")
                service.startGame("11111", created.playerId)

                val advanced = service.sweepExpiredTimeouts(now = 0L)

                assertEquals(emptyList(), advanced)
                verify(exactly = 0) { eventPublisher.publishEvent(any<GameStateChangedEvent>()) }
            }
    }
