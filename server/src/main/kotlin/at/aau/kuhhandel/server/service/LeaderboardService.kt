package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.persistence.entity.LeaderboardEntry
import at.aau.kuhhandel.server.persistence.repository.LeaderboardRepository
import at.aau.kuhhandel.shared.utils.GameRankEntry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@Service
class LeaderboardService(
    private val repository: LeaderboardRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Maps the incoming finished game rankings into DB entities.
     */
    @Transactional
    fun storeScores(rankings: List<GameRankEntry>) {
        val entries =
            rankings.map { rank ->
                LeaderboardEntry(
                    playerName = rank.playerName,
                    score = rank.points,
                    quartetCount = rank.quartetCount,
                    totalMoney = rank.totalMoney,
                )
            }
        repository.saveAll(entries)
    }

    /**
     * Deletes entries older than 7 days.
     */
    @Transactional
    fun cleanOldEntries() {
        runCatching {
            val oneWeekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
            repository.deleteByCreatedAtBefore(oneWeekAgo)
        }.onFailure {
            logger.warn("Leaderboard entry cleanup run failed", it)
        }
    }

    /**
     * Fetches all records from the leaderboard table.
     * Primarily used for verification in tests.
     */
    fun getAllEntries(): List<LeaderboardEntry> = repository.findAll()
}
