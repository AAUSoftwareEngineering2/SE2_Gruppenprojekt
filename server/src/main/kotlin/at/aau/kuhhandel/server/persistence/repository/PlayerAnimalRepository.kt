package at.aau.kuhhandel.server.persistence.repository

import at.aau.kuhhandel.server.persistence.entity.GamePlayerEntity
import at.aau.kuhhandel.server.persistence.entity.PlayerAnimalEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PlayerAnimalRepository : JpaRepository<PlayerAnimalEntity, Long> {
    fun findByPlayer(player: GamePlayerEntity): List<PlayerAnimalEntity>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PlayerAnimalEntity pa WHERE pa.player = :player")
    fun deleteByPlayer(
        @Param("player") player: GamePlayerEntity,
    )
}
