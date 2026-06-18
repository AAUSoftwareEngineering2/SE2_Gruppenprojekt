package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.persistence.PostgresDataJpaTest
import at.aau.kuhhandel.server.persistence.entity.LeaderboardEntry
import at.aau.kuhhandel.server.persistence.repository.LeaderboardRepository
import at.aau.kuhhandel.shared.utils.GameRankEntry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@DataJpaTest
@ActiveProfiles("test")
@Import(LeaderboardService::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class LeaderboardServiceTest
    @Autowired
    constructor(
        private val service: LeaderboardService,
        private val repository: LeaderboardRepository,
    ) : PostgresDataJpaTest() {
        @AfterEach
        fun cleanUp() {
            repository.deleteAll()
        }

        @Test
        fun `storeScores maps and saves rank entries into the leaderboard table`() {
            val mockRankings =
                listOf(
                    GameRankEntry(
                        playerId = "player-1",
                        playerName = "Player1",
                        points = 350,
                        quartetCount = 3,
                        totalMoney = 10,
                        isWinner = true,
                    ),
                    GameRankEntry(
                        playerId = "player-2",
                        playerName = "Player2",
                        points = 120,
                        quartetCount = 1,
                        totalMoney = 50,
                        isWinner = false,
                    ),
                )

            service.storeScores(mockRankings)

            val stored = repository.findAll()
            assertEquals(2, stored.size)

            val alice = stored.first { it.playerName == "Player1" }
            assertEquals(350, alice.score)
            assertEquals(3, alice.quartetCount)
            assertEquals(10, alice.totalMoney)

            val bob = stored.first { it.playerName == "Player2" }
            assertEquals(120, bob.score)
            assertEquals(1, bob.quartetCount)
            assertEquals(50, bob.totalMoney)
        }

        @Test
        fun `cleanOldEntries purges rows older than 7 days and keeps fresh ones`() {
            val now = System.currentTimeMillis()
            val eightDaysAgo = now - TimeUnit.DAYS.toMillis(8)
            val sixDaysAgo = now - TimeUnit.DAYS.toMillis(6)

            // Manually save entries with manipulated timestamps
            val oldEntry =
                LeaderboardEntry(
                    playerName = "OldPlayer",
                    score = 100,
                    quartetCount = 1,
                    totalMoney = 0,
                    createdAt = eightDaysAgo,
                )
            val freshEntry =
                LeaderboardEntry(
                    playerName = "FreshPlayer",
                    score = 200,
                    quartetCount = 2,
                    totalMoney = 30,
                    createdAt = sixDaysAgo,
                )
            repository.saveAll(listOf(oldEntry, freshEntry))

            // Run the scheduled cleaning cycle method
            service.cleanOldEntries()

            // Confirm that the entry older than 7 days was deleted, and the fresher one remains
            val remaining = repository.findAll()
            assertEquals(1, remaining.size)
            assertEquals("FreshPlayer", remaining.single().playerName)
            assertEquals(200, remaining.single().score)
            assertEquals(2, remaining.single().quartetCount)
            assertEquals(30, remaining.single().totalMoney)
        }
    }
