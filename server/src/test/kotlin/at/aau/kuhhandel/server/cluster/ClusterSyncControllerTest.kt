package at.aau.kuhhandel.server.cluster

import at.aau.kuhhandel.server.event.GameStateChangedEvent
import at.aau.kuhhandel.server.persistence.GamePersistenceService
import at.aau.kuhhandel.shared.model.GameState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

class ClusterSyncControllerTest {
    private val persistenceService = mockk<GamePersistenceService>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private fun controller(
        properties: ClusterProperties,
    ): Pair<ClusterSyncController, ClusterUpdateNotifier> {
        val notifier = ClusterUpdateNotifier(properties)
        return ClusterSyncController(properties, notifier, persistenceService, eventPublisher) to
            notifier
    }

    private fun clusterProperties() =
        ClusterProperties(peers = listOf("http://localhost:9999"), secret = "s3cret")

    @Test
    fun `cluster properties reject peers without a secret`() {
        assertThrows<IllegalArgumentException> {
            ClusterProperties(peers = listOf("http://localhost:9999"), secret = "")
        }
    }

    @Test
    fun `rejects requests with a wrong or missing secret`() {
        val (controller, _) = controller(clusterProperties())

        val wrongSecret =
            controller.gameUpdated("wrong", "other-pod", GameUpdatedNotification("12345"))
        val missingSecret =
            controller.gameUpdated(
                null,
                "other-pod",
                GameUpdatedNotification("12345"),
            )

        assertEquals(HttpStatus.FORBIDDEN, wrongSecret.statusCode)
        assertEquals(HttpStatus.FORBIDDEN, missingSecret.statusCode)
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `rejects everything when the cluster is not configured`() {
        val (controller, _) = controller(ClusterProperties())

        val response = controller.gameUpdated("", "other-pod", GameUpdatedNotification("12345"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `drops the echo of its own notification`() {
        val (controller, notifier) = controller(clusterProperties())

        val response =
            controller.gameUpdated("s3cret", notifier.instanceId, GameUpdatedNotification("12345"))

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `reloads the state and republishes it as a local event`() {
        val (controller, _) = controller(clusterProperties())
        val state = GameState()
        every { persistenceService.loadGameState("12345") } returns state

        val response =
            controller.gameUpdated("s3cret", "other-pod", GameUpdatedNotification("12345"))

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        verify(exactly = 1) { eventPublisher.publishEvent(GameStateChangedEvent("12345", state)) }
    }

    @Test
    fun `ignores updates for unknown games`() {
        val (controller, _) = controller(clusterProperties())
        every { persistenceService.loadGameState("99999") } returns null

        val response =
            controller.gameUpdated("s3cret", "other-pod", GameUpdatedNotification("99999"))

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }
}
