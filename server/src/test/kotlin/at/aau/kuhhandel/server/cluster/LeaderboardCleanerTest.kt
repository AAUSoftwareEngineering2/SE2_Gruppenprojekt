package at.aau.kuhhandel.server.cluster

import at.aau.kuhhandel.server.service.LeaderboardService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class LeaderboardCleanerTest {
    @Test
    fun `clean delegates to the leaderboard service`() {
        val leaderboardService = mockk<LeaderboardService>()
        every { leaderboardService.cleanOldEntries() } returns Unit

        LeaderboardCleaner(leaderboardService).clean()

        verify { leaderboardService.cleanOldEntries() }
    }

    @Test
    fun `clean swallows exceptions so the schedule keeps firing`() {
        val leaderboardService = mockk<LeaderboardService>()
        every { leaderboardService.cleanOldEntries() } throws RuntimeException("db down")

        // Must not propagate; otherwise one failure would kill the scheduled task.
        LeaderboardCleaner(leaderboardService).clean()

        verify { leaderboardService.cleanOldEntries() }
    }
}
