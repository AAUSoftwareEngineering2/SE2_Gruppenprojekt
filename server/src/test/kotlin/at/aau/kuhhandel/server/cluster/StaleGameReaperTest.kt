package at.aau.kuhhandel.server.cluster

import at.aau.kuhhandel.server.service.GameService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class StaleGameReaperTest {
    // reap() passes a computed cutoff timestamp, so match any value.
    @Test
    fun `reap delegates to the game service`() {
        val gameService = mockk<GameService>()
        every { gameService.reapStaleGames(any()) } returns listOf("12345")

        StaleGameReaper(gameService, staleAfterMs = 1_800_000L).reap()

        verify { gameService.reapStaleGames(any()) }
    }

    @Test
    fun `reap swallows exceptions so the schedule keeps firing`() {
        val gameService = mockk<GameService>()
        every { gameService.reapStaleGames(any()) } throws RuntimeException("db down")

        // Must not propagate; otherwise one failure would kill the scheduled task.
        StaleGameReaper(gameService, staleAfterMs = 1_800_000L).reap()

        verify { gameService.reapStaleGames(any()) }
    }
}
