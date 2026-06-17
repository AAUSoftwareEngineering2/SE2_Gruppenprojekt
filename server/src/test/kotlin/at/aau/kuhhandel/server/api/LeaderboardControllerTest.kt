package at.aau.kuhhandel.server.api

import at.aau.kuhhandel.server.persistence.entity.LeaderboardEntry
import at.aau.kuhhandel.server.persistence.repository.LeaderboardRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LeaderboardControllerTest {
    private val repository = mockk<LeaderboardRepository>()
    private val controller = LeaderboardController(repository)

    @Test
    fun `getLeaderboard converts database entries into ranked global leaderboard items`() {
        // Arrange: Prepare unranked mock database entries from our top 10 query
        val mockDbEntries =
            listOf(
                LeaderboardEntry(
                    id = 1L,
                    playerName = "Player1",
                    score = 500,
                    quartetCount = 4,
                    totalMoney = 20,
                ),
                LeaderboardEntry(
                    id = 2L,
                    playerName = "Player2",
                    score = 400,
                    quartetCount = 2,
                    totalMoney = 90,
                ),
            )

        every { repository.findTop10ByOrderByScoreDescTotalMoneyDesc() } returns mockDbEntries

        // Act: Invoke the controller method directly as a plain unit test
        val response = controller.getLeaderboard()

        // Assert: Verify the transformation to the clean GlobalLeaderboardItem shape
        assertEquals(2, response.size)

        // Assert first ranked item properties
        val firstItem = response[0]
        assertEquals(1, firstItem.rank) // 1st place calculated via index
        assertEquals("Player1", firstItem.playerName)
        assertEquals(500, firstItem.score)
        assertEquals(4, firstItem.quartetCount)
        assertEquals(20, firstItem.totalMoney)

        // Assert second ranked item properties
        val secondItem = response[1]
        assertEquals(2, secondItem.rank) // 2nd place calculated via index
        assertEquals("Player2", secondItem.playerName)
        assertEquals(400, secondItem.score)
        assertEquals(2, secondItem.quartetCount)
        assertEquals(90, secondItem.totalMoney)

        // Confirm the correct derived query method was targeted
        verify(exactly = 1) { repository.findTop10ByOrderByScoreDescTotalMoneyDesc() }
    }
}
