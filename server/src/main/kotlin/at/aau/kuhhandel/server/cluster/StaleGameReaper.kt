package at.aau.kuhhandel.server.cluster

import at.aau.kuhhandel.server.service.GameService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Periodically purges games that have seen no player activity for a while.
 *
 * The stateless backend keeps phase timers in the database, and the [TimeoutSweeper] advances any
 * game whose timer expired. A game whose players are all gone (app closed, pod replaced) is never
 * cleaned up on its own, so without this reaper the sweeper would advance such "zombie" games on
 * every pod forever, swamping the database. Timeout advances deliberately do not refresh the
 * activity timestamp, so only abandoned games go stale here.
 *
 * Runs on every pod; the delete is idempotent, so concurrent runs are harmless.
 */
@Component
@ConditionalOnProperty(
    prefix = "kuhhandel.cluster.stale-game-reaper",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class StaleGameReaper(
    private val gameService: GameService,
    @Value("\${kuhhandel.cluster.stale-game-reaper.stale-after-ms:1800000}")
    private val staleAfterMs: Long,
) {
    private val logger = LoggerFactory.getLogger(StaleGameReaper::class.java)

    @Scheduled(
        fixedDelayString = "\${kuhhandel.cluster.stale-game-reaper.interval-ms:60000}",
        initialDelayString = "\${kuhhandel.cluster.stale-game-reaper.initial-delay-ms:60000}",
    )
    fun reap() {
        runCatching { gameService.reapStaleGames(System.currentTimeMillis() - staleAfterMs) }
            .onFailure { logger.warn("Stale game reap run failed", it) }
    }
}
