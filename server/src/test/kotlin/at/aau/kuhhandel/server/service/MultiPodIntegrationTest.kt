package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.persistence.GamePersistenceService
import at.aau.kuhhandel.shared.enums.GamePhase
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Two [GameService] instances ("pod A" and "pod B") that share nothing but the database.
 * Runs without a test transaction because cross-instance visibility and row locking only
 * exist across committed transactions.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(GamePersistenceService::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MultiPodIntegrationTest
    @Autowired
    constructor(
        private val persistenceService: GamePersistenceService,
    ) {
        private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        private val usedGameIds = mutableListOf<String>()

        private fun pod(vararg codes: String): GameService {
            val queue = ArrayDeque(codes.toList())
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
        fun `a game created on pod A can be joined and started on pod B`() =
            runTest {
                val podA = pod("41111")
                val podB = pod()

                val created = podA.createGame("Alice")
                podA.joinGame("41111", "Carol")
                val joined = podB.joinGame("41111", "Bob")

                assertEquals(3, joined.gameState.players.size)

                // Pod B starts the game even though pod A created it.
                val started = podB.startGame("41111", created.playerId)
                assertEquals(GamePhase.PLAYER_CHOICE, started.phase)

                // Pod A sees the started game without any pod-to-pod state transfer.
                assertEquals(GamePhase.PLAYER_CHOICE, podA.getGame("41111")?.state?.phase)
            }

        @Test
        fun `a reconnect token issued via pod A validates on pod B`() =
            runTest {
                val podA = pod("42222")
                val podB = pod()

                val created = podA.createGame("Alice")
                podA.storeReconnectToken("42222", created.playerId, "token-from-pod-a")

                assertTrue(
                    podB.isReconnectTokenValid("42222", created.playerId, "token-from-pod-a"),
                )
                assertEquals(
                    false,
                    podB.isReconnectTokenValid("42222", created.playerId, "forged-token"),
                )
            }

        @Test
        fun `concurrent bids from two pods cannot lose an update`() =
            runTest {
                val podA = pod("43333")
                val podB = pod()

                val created = podA.createGame("Alice")
                podA.joinGame("43333", "Carol")
                val joined = podB.joinGame("43333", "Bob")
                val started = podA.startGame("43333", created.playerId)

                // startGame shuffles the seat order; the active player chooses the auction.
                val activePlayerId = started.players[started.currentPlayerIndex].id
                val auctionState = podA.chooseAuction("43333", activePlayerId)
                assertEquals(GamePhase.AUCTION_BIDDING, auctionState.phase)

                val bidderId =
                    listOf(created.playerId, joined.playerId)
                        .first { it != auctionState.auctionState?.auctioneerId }

                // Both pods bid on the same game at the same time. Without the row lock the
                // slower save would overwrite the faster one.
                val results =
                    listOf(
                        async(Dispatchers.IO) {
                            runCatching { podA.placeBid("43333", bidderId, 10) }
                        },
                        async(Dispatchers.IO) {
                            runCatching { podB.placeBid("43333", bidderId, 20) }
                        },
                    ).awaitAll()

                // The higher bid must survive; the lower one either got overbid or rejected.
                assertTrue(results.any { it.isSuccess })

                val persisted = assertNotNull(persistenceService.loadGameState("43333"))
                assertEquals(20, persisted.auctionState?.highestBid)
                assertEquals(bidderId, persisted.auctionState?.highestBidderId)
            }

        @Test
        fun `expired timers are advanced by whichever pod sweeps first and only once`() =
            runTest {
                val podA = pod("44444")
                val podB = pod()

                val created = podA.createGame("Alice")
                podA.joinGame("44444", "Bob")
                podA.joinGame("44444", "Carol")
                val started = podA.startGame("44444", created.playerId)
                val deadline = assertNotNull(started.timerEnd)

                // Pod B (which never touched this game) sweeps after the deadline.
                val advancedByB = podB.sweepExpiredTimeouts(now = deadline + 1)
                assertEquals(listOf("44444"), advancedByB)

                // Pod A sweeping at the same instant must not advance the game a second time.
                val advancedByA = podA.sweepExpiredTimeouts(now = deadline + 1)
                assertEquals(emptyList(), advancedByA)
            }
    }
