package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.persistence.GamePersistenceService
import at.aau.kuhhandel.server.persistence.LeaderboardService
import at.aau.kuhhandel.server.persistence.PostgresDataJpaTest
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.AuctionState
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.Player
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Verifies the stateless [GameService] flow against real Postgres: a game persists itself on
 * each mutation and any service instance can pick it up from the database, like a reconnect
 * after a pod replacement.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(GamePersistenceService::class, LeaderboardService::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class GameServicePersistenceTest
    @Autowired
    constructor(
        private val persistenceService: GamePersistenceService,
        private val leaderboardService: LeaderboardService,
    ) : PostgresDataJpaTest() {
        private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

        @Test
        fun `a fresh service instance reloads the game from the persisted snapshot`() {
            val service = GameService(eventPublisher, persistenceService, leaderboardService)
            val created = service.createGame("player1")

            // A fresh service instance simulates a pod restart: no shared memory, only the DB.
            val restartedService =
                GameService(eventPublisher, persistenceService, leaderboardService)

            val reloaded = assertNotNull(restartedService.getGame(created.gameId))
            assertEquals(created.gameId, reloaded.gameId)
            assertEquals(GamePhase.NOT_STARTED, reloaded.state.phase)
            assertEquals(1, reloaded.state.players.size)
            assertEquals("player1", reloaded.state.players[0].name)
        }

        @Test
        fun `purgeGame removes the persisted record`() {
            val service = GameService(eventPublisher, persistenceService, leaderboardService)
            val created = service.createGame("player1")

            service.purgeGame(created.gameId)

            assertNull(service.getGame(created.gameId))
            assertNull(persistenceService.loadGameState(created.gameId))
        }

        @Test
        fun `createGame writes a LOBBY snapshot the moment the game is created`() {
            val service = GameService(eventPublisher, persistenceService, leaderboardService)
            val created = service.createGame("player1")

            val loaded = assertNotNull(persistenceService.loadGameState(created.gameId))
            assertEquals(GamePhase.NOT_STARTED, loaded.phase)
            assertEquals(listOf("player1"), loaded.players.map { it.name })
        }

        @Test
        fun `createGame retries generated codes that already exist in persistence`() {
            val firstService =
                GameService(
                    eventPublisher = eventPublisher,
                    persistenceService = persistenceService,
                    leaderboardService = leaderboardService,
                    gameCodeGenerator = { "12345" },
                )
            firstService.createGame("player1")

            val generatedCodes = ArrayDeque(listOf("12345", "23456"))
            val restartedService =
                GameService(
                    eventPublisher = eventPublisher,
                    persistenceService = persistenceService,
                    leaderboardService = leaderboardService,
                    gameCodeGenerator = { generatedCodes.removeFirst() },
                )

            val created = restartedService.createGame("player2")

            assertEquals("23456", created.gameId)
            assertEquals(
                listOf("player1"),
                persistenceService
                    .loadGameState("12345")
                    ?.players
                    ?.map { player -> player.name },
            )
            assertEquals(
                listOf("player2"),
                persistenceService
                    .loadGameState("23456")
                    ?.players
                    ?.map { player -> player.name },
            )
        }

        @Test
        fun `timeout sweep advances an expired timer regardless of which instance runs it`() {
            persistenceService.saveGameState(
                "34567",
                GameState(
                    phase = GamePhase.AUCTION_RESULT,
                    timerEnd = 1L,
                    currentPlayerIndex = 0,
                    hostPlayerId = "player1",
                    players =
                        listOf(
                            Player(id = "player1", name = "player1"),
                            Player(id = "player2", name = "player2"),
                        ),
                    auctionState =
                        AuctionState(
                            auctionCard = AnimalCard(id = "auction-cow", type = AnimalType.COW),
                            auctioneerId = "player1",
                            buyerId = "player1",
                        ),
                ),
            )

            // The sweeping instance never saw this game in memory, it finds the expired timer
            // in the database.
            val sweeperService = GameService(eventPublisher, persistenceService, leaderboardService)
            val advanced = sweeperService.sweepExpiredTimeouts()

            assertEquals(listOf("34567"), advanced)
            val resolved = assertNotNull(persistenceService.loadGameState("34567"))
            assertEquals(GamePhase.PLAYER_CHOICE, resolved.phase)
            assertNull(resolved.auctionState)
        }
    }
