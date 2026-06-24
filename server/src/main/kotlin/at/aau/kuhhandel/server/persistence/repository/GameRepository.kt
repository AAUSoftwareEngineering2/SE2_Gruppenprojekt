package at.aau.kuhhandel.server.persistence.repository

import at.aau.kuhhandel.server.persistence.entity.GameEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
// JpaRepository = Spring generiert die Implementierung; save/findById/delete/... gibt's gratis. Hier nur Custom-Queries.
interface GameRepository : JpaRepository<GameEntity, Long> {
    /**
     * Loads a game row with `SELECT ... FOR UPDATE`. All game mutations go through this lock,
     * so two pods can never write the same game at once.
     *
     * Native query on purpose: plain FOR UPDATE works on Postgres and H2, the dialect-generated
     * FOR NO KEY UPDATE does not parse on H2.
     */
    // DAS Schloss: rohes SQL SELECT ... FOR UPDATE -> sperrt die Spiel-Zeile, nur ein Pod schreibt zugleich.
    @Query(value = "SELECT * FROM games WHERE id = :id FOR UPDATE", nativeQuery = true)
    fun findWithLockById(
        @Param("id") id: Long,
    ): GameEntity?

    /**
     * Ids of games whose phase timer has expired. Used by the timeout sweeper.
     */
    // IDs mit abgelaufenem Timer -> für den TimeoutSweeper.
    @Query("SELECT g.id FROM GameEntity g WHERE g.timerEnd IS NOT NULL AND g.timerEnd <= :now")
    fun findIdsWithExpiredTimer(
        @Param("now") now: Long,
    ): List<Long>

    /**
     * Ids of games whose last player activity is older than [cutoff], or was never recorded
     * (legacy rows). Used by the stale-game reaper to purge abandoned games.
     */
    // IDs ohne Aktivität seit cutoff (COALESCE: null zählt als 0) -> für den StaleGameReaper.
    @Query("SELECT g.id FROM GameEntity g WHERE COALESCE(g.lastActivityAt, 0) < :cutoff")
    fun findIdsByLastActivityBefore(
        @Param("cutoff") cutoff: Long,
    ): List<Long>

    /**
     * Ids of games with at least one active spy whose expiration deadline has passed. Used by the
     * spy-expiration sweep (the stateless replacement for the old in-memory spy timer).
     */
    // IDs mit abgelaufenem Spy -> für den Spy-Sweep.
    @Query("SELECT g.id FROM GameEntity g WHERE g.earliestSpyExpiry <= :now")
    fun findIdsWithExpiredSpies(
        @Param("now") now: Long,
    ): List<Long>
}
