package at.aau.kuhhandel.server.persistence.repository

import at.aau.kuhhandel.server.persistence.entity.LeaderboardEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface LeaderboardRepository : JpaRepository<LeaderboardEntry, Long> {
    /**
     * Fetches the top 10 rows sorted by score, descending,
     * using total money as a built-in tie-breaker.
     */
    fun findTop10ByOrderByScoreDescTotalMoneyDesc(): List<LeaderboardEntry>

    /**
     * Deletes all leaderboard entries older than the given cutoff timestamp.
     * Used by the cleanup scheduler to enforce the 1-week retention rule.
     */
    @Modifying
    @Transactional
    fun deleteByCreatedAtBefore(expiryTime: Long)
}
