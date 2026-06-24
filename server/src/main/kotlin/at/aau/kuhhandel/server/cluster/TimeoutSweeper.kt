package at.aau.kuhhandel.server.cluster

import at.aau.kuhhandel.server.service.GameService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Polls the database for expired phase timers and advances those games. Replaces the old
 * in-memory timeout coroutines, which died together with their pod. Runs on every pod; the
 * deadline re-check under the row lock makes sure only one pod actually advances a game.
 */
@Component
// Abschalten ohne Code-Änderung: Property kuhhandel.cluster.timeout-sweeper.enabled=false setzen
// (z. B. in application.yml) -> dann wird diese Bean gar nicht erst erzeugt.
@ConditionalOnProperty(
    prefix = "kuhhandel.cluster.timeout-sweeper",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class TimeoutSweeper(
    private val gameService: GameService,
) {
    private val logger = LoggerFactory.getLogger(TimeoutSweeper::class.java)

    // fixedDelay = wartet N ms NACH Ende des vorigen Laufs bis zum nächsten (fixedRate wäre von
    // Start zu Start, egal wie lang der Lauf dauert). initialDelay = Wartezeit vor dem 1. Lauf.
    @Scheduled(
        fixedDelayString = "\${kuhhandel.cluster.timeout-sweeper.interval-ms:250}",
        initialDelayString = "\${kuhhandel.cluster.timeout-sweeper.initial-delay-ms:0}",
    )
    fun sweep() {
        // Schutz gegen doppeltes Weiterschalten (steckt in sweepExpiredTimeouts): erst eine
        // lock-freie Query "welche Spiele könnten dran sein", dann pro Spiel ein DB-Row-Lock
        // (SELECT ... FOR UPDATE) und UNTER dem Lock nochmal prüfen, ob die Deadline noch offen ist.
        // So schaltet nur der erste Pod weiter; der zweite sieht "schon erledigt" und tut nichts.
        runCatching { gameService.sweepExpiredTimeouts() }
            .onSuccess { advanced ->
                if (advanced.isNotEmpty()) {
                    logger.info("Timeout sweep advanced games: {}", advanced)
                }
            }.onFailure { logger.warn("Timeout sweep run failed", it) }

        // Spy reveals expire on their own deadline (independent of the phase timer), so clear
        // them here too. Replaces the old in-memory spy timer that died with its pod.
        runCatching { gameService.sweepExpiredSpies() }
            .onSuccess { cleared ->
                if (cleared.isNotEmpty()) {
                    logger.info("Spy expiration sweep cleared games: {}", cleared)
                }
            }.onFailure { logger.warn("Spy expiration sweep run failed", it) }
    }
}
