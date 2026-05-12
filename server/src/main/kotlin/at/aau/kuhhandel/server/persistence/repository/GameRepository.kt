package at.aau.kuhhandel.server.persistence.repository

import at.aau.kuhhandel.server.persistence.entity.GameEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GameRepository : JpaRepository<GameEntity, Long>
