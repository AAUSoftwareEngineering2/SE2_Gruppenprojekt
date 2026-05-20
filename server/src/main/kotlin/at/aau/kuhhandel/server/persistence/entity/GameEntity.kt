package at.aau.kuhhandel.server.persistence.entity

import at.aau.kuhhandel.shared.enums.AnimalType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version

@Entity
@Table(name = "games")
class GameEntity(
    @Id
    @Column(name = "id")
    var id: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: GameStatus = GameStatus.LOBBY,
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "active_player_id")
    var activePlayer: UserEntity? = null,
    @Column(name = "round_number", nullable = false)
    var roundNumber: Int = 0,
    @Enumerated(EnumType.STRING)
    @Column(name = "face_up_animal_type", length = 16)
    var faceUpAnimalType: AnimalType? = null,
    @Version
    @Column(name = "version", nullable = false)
    var version: Int = 0,
)
