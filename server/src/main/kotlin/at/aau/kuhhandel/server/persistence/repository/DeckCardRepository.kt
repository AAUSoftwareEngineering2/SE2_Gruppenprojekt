package at.aau.kuhhandel.server.persistence.repository

import at.aau.kuhhandel.server.persistence.entity.DeckCardEntity
import at.aau.kuhhandel.server.persistence.entity.GameEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface DeckCardRepository : JpaRepository<DeckCardEntity, Long> {
    fun findByGameOrderByDrawOrderAsc(game: GameEntity): List<DeckCardEntity>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM DeckCardEntity dc WHERE dc.game = :game")
    fun deleteByGame(
        @Param("game") game: GameEntity,
    )
}
