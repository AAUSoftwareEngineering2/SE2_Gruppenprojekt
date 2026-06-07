package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.persistence.GamePersistenceService
import at.aau.kuhhandel.server.persistence.PostgresDataJpaTest
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.AuctionState
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.Player
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.ArrayDeque
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Verifies that the in-memory [GameService] flow integrates with the persistence layer: a game
 * persists itself on each mutation and can be reloaded from the database after the live session is
 * dropped, which is how a reconnect after a WebSocket close looks.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(GamePersistenceService::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class GameServicePersistenceTest
    @Autowired
    constructor(
        private val persistenceService: GamePersistenceService,
    ) : PostgresDataJpaTest() {
        private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

        @Test
        fun `getGame reloads a removed session from the persisted snapshot`() {
            val service = GameService(eventPublisher, persistenceService)
            val created = service.createGame("player-1")

            service.removeGame(created.gameId)

            val reloaded = assertNotNull(service.getGame(created.gameId))
            assertEquals(created.gameId, reloaded.gameId)
            assertEquals(GamePhase.NOT_STARTED, reloaded.state.phase)
            assertEquals(1, reloaded.state.players.size)
            assertEquals("player-1", reloaded.state.players[0].name)
        }

        @Test
        fun `purgeGame removes both the in-memory session and the persisted record`() {
            val service = GameService(eventPublisher, persistenceService)
            val created = service.createGame("player-1")

            service.purgeGame(created.gameId)

            assertNull(service.getGame(created.gameId))
            assertNull(persistenceService.loadGameState(created.gameId))
        }

        @Test
        fun `createGame writes a LOBBY snapshot the moment the game is created`() {
            val service = GameService(eventPublisher, persistenceService)
            val created = service.createGame("player-1")

            val loaded = assertNotNull(persistenceService.loadGameState(created.gameId))
            assertEquals(GamePhase.NOT_STARTED, loaded.phase)
            assertEquals(listOf("player-1"), loaded.players.map { it.name })
        }

        @Test
        fun `createGame retries generated codes that already exist in persistence`() {
            val firstService =
                GameService(
                    eventPublisher = eventPublisher,
                    persistenceService = persistenceService,
                    gameCodeGenerator = { "12345" },
                )
            firstService.createGame("player-1")

            val generatedCodes = ArrayDeque(listOf("12345", "23456"))
            val restartedService =
                GameService(
                    eventPublisher = eventPublisher,
                    persistenceService = persistenceService,
                    gameCodeGenerator = { generatedCodes.removeFirst() },
                )

            val created = restartedService.createGame("player-2")

            assertEquals("23456", created.gameId)
            assertEquals(
                listOf("player-1"),
                persistenceService
                    .loadGameState("12345")
                    ?.players
                    ?.map { player -> player.name },
            )
            assertEquals(
                listOf("player-2"),
                persistenceService
                    .loadGameState("23456")
                    ?.players
                    ?.map { player -> player.name },
            )
        }

        @Test
        @OptIn(ExperimentalCoroutinesApi::class)
        fun `getGame revives an expired persisted timer after loading from database`() =
            runTest {
                persistenceService.saveGameState(
                    "34567",
                    GameState(
                        phase = GamePhase.AUCTION_RESULT,
                        timerEnd = 1L,
                        currentPlayerIndex = 0,
                        hostPlayerId = "player-1",
                        players =
                            listOf(
                                Player(id = "player-1", name = "player-1"),
                                Player(id = "player-2", name = "player-2"),
                            ),
                        auctionState =
                            AuctionState(
                                auctionCard = AnimalCard(id = "auction-cow", type = AnimalType.COW),
                                auctioneerId = "player-1",
                                buyerId = "player-1",
                            ),
                    ),
                )
                val service =
                    GameService(
                        eventPublisher = eventPublisher,
                        persistenceService = persistenceService,
                        serviceScope = backgroundScope,
                    )

                val reloaded = assertNotNull(service.getGame("34567"))
                assertEquals(GamePhase.AUCTION_RESULT, reloaded.state.phase)

                runCurrent()

                assertEquals(GamePhase.PLAYER_CHOICE, reloaded.state.phase)
                assertNull(reloaded.state.auctionState)
                service.removeGame("34567")
            }
    }
