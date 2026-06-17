package at.aau.kuhhandel.server.cluster

import at.aau.kuhhandel.server.service.LeaderboardService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Periodically purges leaderboard entries older than 7 days.
 *
 * Runs on every pod; the date-cutoff delete is idempotent, meaning concurrent runs
 * on multiple pods safely clear the same historical data without conflict.
 */
@Component
@ConditionalOnProperty(
    prefix = "kuhhandel.cluster.leaderboard-cleaner",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class LeaderboardCleaner(
    private val leaderboardService: LeaderboardService,
) {
    private val logger = LoggerFactory.getLogger(LeaderboardCleaner::class.java)

    @Scheduled(
        fixedDelayString = "\${kuhhandel.cluster.leaderboard-cleaner.interval-ms:86400000}",
        initialDelayString = "\${kuhhandel.cluster.leaderboard-cleaner.initial-delay-ms:60000}",
    )
    fun clean() {
        runCatching { leaderboardService.cleanOldEntries() }
            .onFailure { logger.warn("Leaderboard cleanup run failed", it) }
    }
}
