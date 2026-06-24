package at.aau.kuhhandel.server.cluster

import at.aau.kuhhandel.server.service.GameService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class TimeoutSweeperTest {
    // sweep() passes the current time as the (default) argument, so match any value.
    @Test
    // testet: dass sweep() an gameService.sweepExpiredTimeouts delegiert.
    fun `sweep delegates to the game service`() {
        val gameService = mockk<GameService>()
        every { gameService.sweepExpiredTimeouts(any()) } returns listOf("12345")

        TimeoutSweeper(gameService).sweep()

        verify { gameService.sweepExpiredTimeouts(any()) }
    }

    @Test
    // testet: dass sweep() Exceptions schluckt, damit der geplante Task weiterlaeuft.
    fun `sweep swallows exceptions so the schedule keeps firing`() {
        val gameService = mockk<GameService>()
        every { gameService.sweepExpiredTimeouts(any()) } throws RuntimeException("db down")

        // Must not propagate; otherwise one failure would kill the scheduled task.
        TimeoutSweeper(gameService).sweep()

        verify { gameService.sweepExpiredTimeouts(any()) }
    }
}
