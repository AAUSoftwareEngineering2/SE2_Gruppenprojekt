package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.cluster.ClusterUpdateNotifier
import at.aau.kuhhandel.server.event.GameStateChangedEvent
import at.aau.kuhhandel.server.exception.GameException
import at.aau.kuhhandel.server.persistence.GamePersistenceService
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GameErrorReason
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.AuctionState
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.MoneyCard
import at.aau.kuhhandel.shared.model.Player
import at.aau.kuhhandel.shared.model.SpyAction
import at.aau.kuhhandel.shared.utils.GameRankEntry
import at.aau.kuhhandel.shared.utils.ScoreCalculator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
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
@Import(GamePersistenceService::class, LeaderboardService::class)
// No test transaction: the service commits its row-locked transactions on Dispatchers.IO
// threads anyway, so we clean up explicitly instead.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GameServiceTest
    @Autowired
    constructor(
        private val persistenceService: GamePersistenceService,
        private val leaderboardService: LeaderboardService,
    ) {
        private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        private val usedGameIds = mutableListOf<String>()

        private fun service(
            codes: List<String> = emptyList(),
            clusterNotifier: ClusterUpdateNotifier? = null,
        ): GameService {
            val queue = ArrayDeque(codes)
            usedGameIds += codes
            return if (codes.isEmpty()) {
                GameService(
                    eventPublisher,
                    persistenceService,
                    leaderboardService,
                    clusterNotifier = clusterNotifier,
                )
            } else {
                GameService(
                    eventPublisher,
                    persistenceService,
                    leaderboardService,
                    clusterNotifier = clusterNotifier,
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

            val result = service.createGame("Player1")
            usedGameIds += result.gameId

            assertEquals(5, result.gameId.length)
            assertEquals(GamePhase.NOT_STARTED, result.gameState.phase)
            assertEquals(
                "Player1",
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
            assertTrue(result.reconnectToken.isNotBlank())
            assertTrue(
                persistenceService.isReconnectTokenValid(
                    result.gameId,
                    result.playerId,
                    result.reconnectToken,
                ),
            )
            // The lobby snapshot lands in the database immediately.
            assertNotNull(persistenceService.loadGameState(result.gameId))
        }

        @Test
        fun `createGame generates different codes`() {
            val service = service()

            val first = service.createGame("Player1")
            val second = service.createGame("Player1")
            usedGameIds += listOf(first.gameId, second.gameId)

            assertNotEquals(first.gameId, second.gameId)
        }

        @Test
        fun `getGame loads the persisted state and returns null for unknown ids`() {
            val service = service(codes = listOf("11111"))
            service.createGame("Player1")

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
                service.createGame("Player1")

                val joinResult = service.joinGame("11111", "Player2")

                assertEquals("11111", joinResult.gameId)
                assertEquals(2, joinResult.gameState.players.size)
                assertTrue(
                    service.isReconnectTokenValid(
                        "11111",
                        joinResult.playerId,
                        joinResult.reconnectToken,
                    ),
                )
                assertEquals(
                    listOf("Player1", "Player2"),
                    persistenceService.loadGameState("11111")?.players?.map { it.name },
                )
            }

        @Test
        fun `joinGame throws GAME_NOT_FOUND for unknown games`() =
            runTest {
                val service = service()

                val exception =
                    assertThrows<GameException> { service.joinGame("99999", "Player2") }
                assertEquals(GameErrorReason.GAME_NOT_FOUND, exception.reason)
            }

        @Test
        fun `leaveGame removes the player and purges an empty game`() =
            runTest {
                val service = service(codes = listOf("11111"))
                val created = service.createGame("Player1")
                val joined = service.joinGame("11111", "Player2")

                val afterLeave = service.leaveGame("11111", joined.playerId)
                assertEquals(listOf(created.playerId), afterLeave.players.map { it.id })

                service.leaveGame("11111", created.playerId)
                // Last player gone -> game fully purged from the database.
                assertNull(persistenceService.loadGameState("11111"))
            }

        @Test
        fun `leaveGame rejects joins after the last player leaves`() =
            runTest {
                lateinit var service: GameService
                var joinAttemptReason: GameErrorReason? = null
                var attemptedJoin = false
                val notifier = mockk<ClusterUpdateNotifier>()
                every { notifier.gameUpdated("11111") } answers {
                    if (!attemptedJoin) {
                        attemptedJoin = true
                        joinAttemptReason =
                            runCatching {
                                runBlocking { service.joinGame("11111", "Player2") }
                            }.exceptionOrNull()
                                ?.let { it as GameException }
                                ?.reason
                    }
                }

                service = service(codes = listOf("11111"), clusterNotifier = notifier)
                val created = service.createGame("Player1")

                service.leaveGame("11111", created.playerId)

                assertEquals(GameErrorReason.GAME_NOT_FOUND, joinAttemptReason)
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
        fun `disconnectPlayer removes player from room in lobby phase`() =
            runTest {
                val service = service(codes = listOf("11111"))
                service.createGame("Player1")
                val guest = service.joinGame("11111", "Player2")

                // Player is connected, so guard passes. Hits lobby block -> removes player.
                val state = service.disconnectPlayer("11111", guest.playerId)

                assertEquals(1, state.players.size)
                assertTrue(state.players.none { it.id == guest.playerId })
                // Other players remain, so the game must survive. (Regression: the old two-step
                // disconnect purged the whole game whenever the disconnecting player was gone,
                // even with players left.)
                val dbState = assertNotNull(persistenceService.loadGameState("11111"))
                assertEquals(1, dbState.players.size)
            }

        @Test
        fun `disconnectPlayer marks player as disconnected`() =
            runTest {
                val service = service(codes = listOf("11111"))
                val host = service.createGame("Player1")
                val guest = service.joinGame("11111", "Player2")
                service.joinGame("11111", "Player3")
                service.startGame("11111", host.playerId)

                // Player is connected, guard passes. Hits active game block -> flags false.
                val state = service.disconnectPlayer("11111", guest.playerId)

                assertFalse(state.players.first { it.id == guest.playerId }.isConnected)

                // Verify stateless database reload round-trip
                val dbState = assertNotNull(persistenceService.loadGameState("11111"))
                assertFalse(dbState.players.first { it.id == guest.playerId }.isConnected)
            }

        @Test
        fun `reconnectPlayer reconnects disconnected player`() =
            runTest {
                val service = service(codes = listOf("11111"))
                val host = service.createGame("Player1")
                val guest = service.joinGame("11111", "Player2")
                service.joinGame("11111", "Player3")
                service.startGame("11111", host.playerId)

                // Disconnect the player first so their flag is false
                service.disconnectPlayer("11111", guest.playerId)

                // Reconnect the player. Flag is false, guard passes -> flags true.
                val state = service.reconnectPlayer("11111", guest.playerId)

                assertTrue(state.players.first { it.id == guest.playerId }.isConnected)

                // Verify stateless database reload round-trip
                val dbState = assertNotNull(persistenceService.loadGameState("11111"))
                assertTrue(dbState.players.first { it.id == guest.playerId }.isConnected)
            }

        @Test
        fun `getStateForReconnection validates and rotates reconnect token`() =
            runTest {
                val service = service(codes = listOf("11111"))
                val host = service.createGame("Player1")
                val guest = service.joinGame("11111", "Player2")
                service.joinGame("11111", "Player3")
                service.startGame("11111", host.playerId)
                service.disconnectPlayer("11111", guest.playerId)

                val result =
                    service.getStateForReconnection(
                        "11111",
                        guest.playerId,
                        guest.reconnectToken,
                    )

                assertTrue(
                    result.gameState.players
                        .first { it.id == guest.playerId }
                        .isConnected,
                )
                assertNotEquals(guest.reconnectToken, result.reconnectToken)
                assertEquals(
                    false,
                    service.isReconnectTokenValid("11111", guest.playerId, guest.reconnectToken),
                )
                assertTrue(
                    service.isReconnectTokenValid("11111", guest.playerId, result.reconnectToken),
                )
            }

        @Test
        fun `getStateForReconnection rejects invalid reconnect token`() =
            runTest {
                val service = service(codes = listOf("11111"))
                val created = service.createGame("Player1")

                val exception =
                    assertThrows<GameException> {
                        service.getStateForReconnection("11111", created.playerId, "wrong-token")
                    }
                assertEquals(GameErrorReason.INVALID_RECONNECTION_TOKEN, exception.reason)
            }

        @Test
        fun `reconnectPlayer fails when player is already connected`() =
            runTest {
                val service = service(codes = listOf("11111"))
                val created = service.createGame("Player1")

                // In lobby phase, player is already connected. Guard at top throws immediately.
                val exception =
                    assertThrows<GameException> {
                        service.reconnectPlayer("11111", created.playerId)
                    }
                assertEquals(GameErrorReason.ALREADY_CONNECTED, exception.reason)
            }

        @Test
        fun `startGame runs the real state machine against persisted state`() =
            runTest {
                val service = service(codes = listOf("11111"))
                val created = service.createGame("Player1")
                service.joinGame("11111", "Player2")
                service.joinGame("11111", "Player3")

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
                val created = service.createGame("Player1")

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
                val created = service.createGame("Player1")

                service.storeReconnectToken("11111", created.playerId, "token-1")

                val firstFingerprint =
                    assertNotNull(service.reconnectTokenFingerprint("11111", created.playerId))
                assertEquals(64, firstFingerprint.length)
                assertNotEquals("token-1", firstFingerprint)
                assertTrue(service.isReconnectTokenValid("11111", created.playerId, "token-1"))
                assertEquals(
                    false,
                    service.isReconnectTokenValid("11111", created.playerId, "wrong"),
                )

                // Token rotation invalidates the previous token.
                service.storeReconnectToken("11111", created.playerId, "token-2")
                val secondFingerprint =
                    assertNotNull(service.reconnectTokenFingerprint("11111", created.playerId))
                assertNotEquals(firstFingerprint, secondFingerprint)
                assertNotEquals("token-2", secondFingerprint)
                assertEquals(
                    false,
                    service.isReconnectTokenValid("11111", created.playerId, "token-1"),
                )
                assertTrue(service.isReconnectTokenValid("11111", created.playerId, "token-2"))
            }

        @Test
        fun `purgeGame deletes reconnect tokens with player rows`() =
            runTest {
                val service = service(codes = listOf("11111"))
                val created = service.createGame("Player1")
                val joined = service.joinGame("11111", "Player2")

                assertNotNull(service.reconnectTokenFingerprint("11111", created.playerId))
                assertNotNull(service.reconnectTokenFingerprint("11111", joined.playerId))

                service.purgeGame("11111")

                assertNull(service.reconnectTokenFingerprint("11111", created.playerId))
                assertNull(service.reconnectTokenFingerprint("11111", joined.playerId))
            }

        @Test
        fun `sweepExpiredTimeouts advances an expired phase and publishes the update`() =
            runTest {
                val service = service(codes = listOf("11111"))
                val created = service.createGame("Player1")
                service.joinGame("11111", "Player2")
                service.joinGame("11111", "Player3")
                service.startGame("11111", created.playerId)

                val expired = assertNotNull(persistenceService.loadGameState("11111"))
                val deadline = assertNotNull(expired.timerEnd)

                // First sweep starts an auction
                val advanced = service.sweepExpiredTimeouts(now = deadline + 1)
                assertEquals(listOf("11111"), advanced)
                verify(exactly = 1) { eventPublisher.publishEvent(any<GameStateChangedEvent>()) }

                // Fetch the freshly generated auction timer from the database row
                val auctionState = assertNotNull(persistenceService.loadGameState("11111"))
                val auctionTimer = assertNotNull(auctionState.timerEnd)

                // Second sweep must happen BEFORE the auction timer runs out
                val secondSweep = service.sweepExpiredTimeouts(now = auctionTimer - 1000L)
                assertEquals(emptyList(), secondSweep)
            }

        @Test
        fun `sweepExpiredTimeouts ignores games whose timer is still running`() =
            runTest {
                val service = service(codes = listOf("11111"))
                val created = service.createGame("Player1")
                service.joinGame("11111", "Player2")
                service.joinGame("11111", "Player3")
                service.startGame("11111", created.playerId)

                val advanced = service.sweepExpiredTimeouts(now = 0L)

                assertEquals(emptyList(), advanced)
                verify(exactly = 0) { eventPublisher.publishEvent(any<GameStateChangedEvent>()) }
            }

        @Test
        fun `submitAuctionPayment completes a persisted payment phase after reload`() =
            runTest {
                val service = service(codes = listOf("11111"))
                persistenceService.saveGameState(
                    "11111",
                    GameState(
                        phase = GamePhase.AUCTION_PAYMENT,
                        players =
                            listOf(
                                Player(
                                    id = "player-1",
                                    name = "Seller",
                                ),
                                Player(
                                    id = "player-2",
                                    name = "Buyer",
                                    moneyCards =
                                        listOf(
                                            MoneyCard("m10-a", 10),
                                            MoneyCard("m10-b", 10),
                                        ),
                                ),
                            ),
                        hostPlayerId = "player-1",
                        auctionState =
                            AuctionState(
                                auctionCard = AnimalCard("cow-1", AnimalType.COW),
                                auctioneerId = "player-1",
                                highestBid = 20,
                                highestBidderId = "player-2",
                                buyerId = "player-2",
                                sellerId = "player-1",
                            ),
                    ),
                )
                val reloaded = assertNotNull(persistenceService.loadGameState("11111"))
                val paymentCardIds =
                    reloaded.players
                        .single { it.id == "player-2" }
                        .moneyCards
                        .mapTo(mutableSetOf()) { it.id }

                val updated =
                    service.submitAuctionPayment(
                        "11111",
                        "player-2",
                        paymentCardIds,
                    )

                val seller = updated.players.single { it.id == "player-1" }
                val buyer = updated.players.single { it.id == "player-2" }
                assertEquals(GamePhase.AUCTION_RESULT, updated.phase)
                assertEquals(20, seller.totalMoney())
                assertTrue(buyer.animals.any { it.type == AnimalType.COW })
                assertEquals(
                    GamePhase.AUCTION_RESULT,
                    persistenceService.loadGameState("11111")?.phase,
                )
            }

        @Test
        fun `reapStaleGames purges games older than the cutoff and keeps fresh ones`() {
            val service = service(codes = listOf("11111"))
            val created = service.createGame("Player1")
            // Pin the activity timestamp to a known point in the past.
            persistenceService.saveGameState(created.gameId, created.gameState, activityAt = 1_000L)

            // A cutoff before the recorded activity reaps nothing.
            assertEquals(emptyList(), service.reapStaleGames(cutoff = 500L))
            assertNotNull(persistenceService.loadGameState(created.gameId))

            // A cutoff after the recorded activity reaps the game.
            assertEquals(listOf(created.gameId), service.reapStaleGames(cutoff = 2_000L))
            assertNull(persistenceService.loadGameState(created.gameId))
        }

        @Test
        fun `player mutations refresh activity but timeout sweeps do not`() {
            val service = service(codes = listOf("11111"))
            val created = service.createGame("Player1")
            persistenceService.saveGameState(created.gameId, created.gameState, activityAt = 1_000L)
            assertEquals(1_000L, persistenceService.lastActivityAt(created.gameId))

            // Sweep-style mutation (activityAt = null) must NOT refresh the activity timestamp.
            persistenceService.mutateGameState(created.gameId, activityAt = null) { it }
            assertEquals(1_000L, persistenceService.lastActivityAt(created.gameId))

            // A player-style mutation refreshes it, keeping the game out of the reaper's reach.
            persistenceService.mutateGameState(created.gameId, activityAt = 5_000L) { it }
            assertEquals(5_000L, persistenceService.lastActivityAt(created.gameId))
        }

        @Test
        fun `createGame rejects a player name with invalid characters`() {
            val service = service()

            val exception = assertThrows<GameException> { service.createGame("bad name!") }
            assertEquals(GameErrorReason.INVALID_PLAYER_NAME, exception.reason)
        }

        @Test
        fun `active spies are persisted and cleared by the expiry sweep`() {
            val service = service(codes = listOf("11111"))
            val created = service.createGame("Player1")
            val lobby = assertNotNull(persistenceService.loadGameState("11111"))

            // Persist a state carrying an already-expired spy reveal.
            val withSpy =
                lobby.copy(
                    activeSpies =
                        setOf(
                            SpyAction(
                                spyId = "spy-1",
                                targetId = created.playerId,
                                expiresAt = 1L,
                                revealedCards = emptySet(),
                            ),
                        ),
                    spiedThisTurn = setOf("spy-1"),
                )
            persistenceService.saveGameState("11111", withSpy)

            // The spy state survives the stateless save/reload round-trip.
            val reloaded = assertNotNull(persistenceService.loadGameState("11111"))
            assertEquals(setOf("spy-1"), reloaded.activeSpies.map { it.spyId }.toSet())
            assertEquals(setOf("spy-1"), reloaded.spiedThisTurn)

            // The sweep clears the expired spy.
            val cleared = service.sweepExpiredSpies(now = 1_000L)
            assertEquals(listOf("11111"), cleared)
            assertTrue(
                assertNotNull(persistenceService.loadGameState("11111")).activeSpies.isEmpty(),
            )
        }

        @Test
        fun `spy persists the active spy reveal across the stateless round-trip`() =
            runTest {
                val service = service(codes = listOf("11111"))
                val host = service.createGame("Player1")
                service.joinGame("11111", "Player2")
                service.joinGame("11111", "Player3")
                val started = service.startGame("11111", host.playerId)

                // The spy is any player whose turn it is not; the target is another player.
                val activeId = started.players[started.currentPlayerIndex].id
                val spyId = started.players.first { it.id != activeId }.id
                val targetId = started.players.first { it.id != spyId }.id

                val afterSpy = service.spy("11111", spyId, targetId)

                assertTrue(
                    afterSpy.activeSpies.any { it.spyId == spyId && it.targetId == targetId },
                )
                assertTrue(
                    assertNotNull(persistenceService.loadGameState("11111"))
                        .activeSpies
                        .any { it.spyId == spyId },
                )
            }

        @Test
        fun `sweepExpiredTimeouts stores final rankings when game finishes`() =
            runTest {
                val service = service(codes = listOf("11111"))
                val created = service.createGame("Player1")
                val lobby = assertNotNull(persistenceService.loadGameState("11111"))

                // Create full quartets for all animal types to trigger the game-end condition
                val allCompletedQuartets =
                    AnimalType.entries.flatMap { type ->
                        // Simulating 4 cards per animal type to make a full quartet
                        List(4) { AnimalCard(id = "${type.name}-$it", type = type) }
                    }

                // Put all completed quartets on Player1
                val playersWithFinishedGame =
                    lobby.players.map { player ->
                        if (player.id == created.playerId) {
                            player.copy(animals = allCompletedQuartets)
                        } else {
                            player
                        }
                    }

                val mockRankings =
                    listOf(
                        GameRankEntry(
                            playerId = created.playerId,
                            playerName = "Player1",
                            points = 500,
                            quartetCount = AnimalType.entries.size,
                            totalMoney = 0,
                            isWinner = true,
                        ),
                    )

                // Simulate being in the trade result phase with an expired timer deadline
                val stateReadyToFinish =
                    lobby.copy(
                        phase = GamePhase.TRADE_RESULT,
                        players = playersWithFinishedGame,
                        timerEnd = 1000L,
                        finalRanking = mockRankings,
                    )
                persistenceService.saveGameState("11111", stateReadyToFinish)

                // Trigger the automatic timeout sweep past the deadline
                service.sweepExpiredTimeouts(now = 2000L)

                // Verify that the GameService caught the transition to the finished phase and saved it
                val storedEntries = leaderboardService.getAllEntries()

                // Calculate the exact expected score dynamically
                val expectedScore = ScoreCalculator.calculateScore(playersWithFinishedGame.first())

                assertEquals(1, storedEntries.size)
                assertEquals("Player1", storedEntries.single().playerName)
                assertEquals(expectedScore, storedEntries.single().score)
            }
    }
