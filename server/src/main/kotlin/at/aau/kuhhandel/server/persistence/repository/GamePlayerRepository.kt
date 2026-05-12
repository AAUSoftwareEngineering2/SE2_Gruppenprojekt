package at.aau.kuhhandel.server.persistence.repository

import at.aau.kuhhandel.server.persistence.entity.GameEntity
import at.aau.kuhhandel.server.persistence.entity.GamePlayerEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface GamePlayerRepository : JpaRepository<GamePlayerEntity, Long> {
    fun findByGameOrderBySeatOrderAsc(game: GameEntity): List<GamePlayerEntity>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GamePlayerEntity gp WHERE gp.game = :game")
    fun deleteByGame(
        @Param("game") game: GameEntity,
    )
}
