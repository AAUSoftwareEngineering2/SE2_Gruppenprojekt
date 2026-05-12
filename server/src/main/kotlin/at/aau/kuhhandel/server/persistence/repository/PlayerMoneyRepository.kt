package at.aau.kuhhandel.server.persistence.repository

import at.aau.kuhhandel.server.persistence.entity.GamePlayerEntity
import at.aau.kuhhandel.server.persistence.entity.PlayerMoneyEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PlayerMoneyRepository : JpaRepository<PlayerMoneyEntity, Long> {
    fun findByPlayer(player: GamePlayerEntity): List<PlayerMoneyEntity>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PlayerMoneyEntity pm WHERE pm.player = :player")
    fun deleteByPlayer(
        @Param("player") player: GamePlayerEntity,
    )
}
