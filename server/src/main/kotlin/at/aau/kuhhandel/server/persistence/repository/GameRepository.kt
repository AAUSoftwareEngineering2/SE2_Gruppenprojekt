package at.aau.kuhhandel.server.persistence.repository

import at.aau.kuhhandel.server.persistence.entity.GameEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface GameRepository : JpaRepository<GameEntity, Long> {
    /**
     * Loads a game row with `SELECT ... FOR UPDATE`. All game mutations go through this lock,
     * so two pods can never write the same game at once.
     *
     * Native query on purpose: plain FOR UPDATE works on Postgres and H2, the dialect-generated
     * FOR NO KEY UPDATE does not parse on H2.
     */
    @Query(value = "SELECT * FROM games WHERE id = :id FOR UPDATE", nativeQuery = true)
    fun findWithLockById(
        @Param("id") id: Long,
    ): GameEntity?

    /**
     * Ids of games whose phase timer has expired. Used by the timeout sweeper.
     */
    @Query("SELECT g.id FROM GameEntity g WHERE g.timerEnd IS NOT NULL AND g.timerEnd <= :now")
    fun findIdsWithExpiredTimer(
        @Param("now") now: Long,
    ): List<Long>
}
