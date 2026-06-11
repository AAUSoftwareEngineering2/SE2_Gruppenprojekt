package at.aau.kuhhandel.server.api

import at.aau.kuhhandel.server.persistence.GamePersistenceService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DebugControllerTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withUserConfiguration(DebugController::class.java)

    @Test
    fun `debug persistence endpoint is disabled by default`() {
        contextRunner.run { context ->
            assertNull(context.getBeanProvider(DebugController::class.java).getIfAvailable())
        }
    }

    @Test
    fun `debug persistence endpoint does not expose database exception details`() {
        val persistenceService = mockk<GamePersistenceService>()
        every { persistenceService.loadGameState("00000") } throws
            IllegalStateException("jdbc:postgresql://db.internal.example/kuhhandel password=secret")

        val response = DebugController(persistenceService).checkPersistence()

        assertEquals("ERROR", response["status"])
        assertEquals("Database connection check failed", response["message"])
    }
}
