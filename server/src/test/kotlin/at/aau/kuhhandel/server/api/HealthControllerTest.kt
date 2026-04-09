package at.aau.kuhhandel.server.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HealthControllerTest {
    private lateinit var controller: HealthController

    @BeforeEach
    fun setUp() {
        controller = HealthController()
    }

    @Test
    fun `health returns UP`() {
        val response = controller.getHealth()

        assertEquals("UP", response.status)
    }
}
