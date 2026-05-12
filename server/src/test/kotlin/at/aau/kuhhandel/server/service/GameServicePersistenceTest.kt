package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.persistence.GamePersistenceService
import at.aau.kuhhandel.shared.enums.GamePhase
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
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
class GameServicePersistenceTest
    @Autowired
    constructor(
        private val persistenceService: GamePersistenceService,
    ) {
        private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

        @Test
        fun `getGame reloads a removed session from the persisted snapshot`() {
            val service = GameService(eventPublisher, persistenceService)
            val session = service.createGame("player-1")
            service.startGame(session.gameId)

            service.removeGame(session.gameId)

            val reloaded = assertNotNull(service.getGame(session.gameId))
            assertEquals(session.gameId, reloaded.gameId)
            assertEquals(GamePhase.PLAYER_TURN, reloaded.gameState.phase)
            assertEquals(1, reloaded.gameState.players.size)
            assertEquals("player-1", reloaded.gameState.players[0].id)
        }

        @Test
        fun `purgeGame removes both the in-memory session and the persisted record`() {
            val service = GameService(eventPublisher, persistenceService)
            val session = service.createGame("player-1")
            service.startGame(session.gameId)

            service.purgeGame(session.gameId)

            assertNull(service.getGame(session.gameId))
            assertNull(persistenceService.loadGameState(session.gameId))
        }

        @Test
        fun `createGame writes a LOBBY snapshot the moment the game is created`() {
            val service = GameService(eventPublisher, persistenceService)
            val session = service.createGame("player-1")

            val loaded = assertNotNull(persistenceService.loadGameState(session.gameId))
            assertEquals(GamePhase.NOT_STARTED, loaded.phase)
            assertEquals(listOf("player-1"), loaded.players.map { it.id })
        }
    }
