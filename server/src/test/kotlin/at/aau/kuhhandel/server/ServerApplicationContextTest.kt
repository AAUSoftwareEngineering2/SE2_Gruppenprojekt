package at.aau.kuhhandel.server

import at.aau.kuhhandel.server.persistence.GamePersistenceService
import at.aau.kuhhandel.server.service.GameService
import at.aau.kuhhandel.server.websocket.GameWebSocketHandler
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertNotNull

/**
 * Boots the full Spring Boot application context against the H2 test database to catch wiring
 * mistakes that the slice tests (`@DataJpaTest`, plain `mock`-based handler tests) would miss —
 * missing beans, autoconfig conflicts, JPA scan misconfiguration, etc.
 */
@SpringBootTest
@ActiveProfiles("test")
class ServerApplicationContextTest
    @Autowired
    constructor(
        private val gameService: GameService,
        private val persistenceService: GamePersistenceService,
        private val webSocketHandler: GameWebSocketHandler,
    ) {
        @Test
        fun `application context boots with all persistence beans wired`() {
            assertNotNull(gameService)
            assertNotNull(persistenceService)
            assertNotNull(webSocketHandler)
        }
    }
