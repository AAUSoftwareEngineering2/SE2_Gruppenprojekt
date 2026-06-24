package at.aau.kuhhandel.server.persistence.repository

import at.aau.kuhhandel.server.persistence.entity.GameEntity
import at.aau.kuhhandel.server.persistence.entity.GamePlayerEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
// JpaRepository = Spring generiert die Implementierung;
interface GamePlayerRepository : JpaRepository<GamePlayerEntity, Long> {
    // abgeleitete Query (aus dem Methodennamen): Spieler eines Spiels, nach Sitzplatz sortiert.
    fun findByGameOrderBySeatOrderAsc(game: GameEntity): List<GamePlayerEntity>

    @Query(
        "SELECT gp FROM GamePlayerEntity gp WHERE gp.game.id = :gameId AND gp.playerId = :playerId",
    )
    // ein Spieler per game+playerId, OHNE Lock.
    fun findByGameIdAndPlayerId(
        @Param("gameId") gameId: Long,
        @Param("playerId") playerId: String,
    ): GamePlayerEntity?

    // wie oben, aber @Lock(PESSIMISTIC_WRITE) -> sperrt die Spieler-Zeile (beim Reconnect, Token-Rotation).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "SELECT gp FROM GamePlayerEntity gp WHERE gp.game.id = :gameId AND gp.playerId = :playerId",
    )
    fun findLockedByGameIdAndPlayerId(
        @Param("gameId") gameId: Long,
        @Param("playerId") playerId: String,
    ): GamePlayerEntity?

    // @Modifying = schreibende Query (kein Select): löscht alle Spieler eines Spiels.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GamePlayerEntity gp WHERE gp.game = :game")
    fun deleteByGame(
        @Param("game") game: GameEntity,
    )
}
