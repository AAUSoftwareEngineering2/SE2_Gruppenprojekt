package at.aau.kuhhandel.server.persistence.entity

import at.aau.kuhhandel.shared.enums.AnimalType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "player_animals",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_player_animals_type",
            columnNames = ["player_id", "animal_type"],
        ),
    ],
)
class PlayerAnimalEntity(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    var player: GamePlayerEntity,
    @Enumerated(EnumType.STRING)
    @Column(name = "animal_type", nullable = false, length = 16)
    var animalType: AnimalType,
    @Column(name = "amount", nullable = false)
    var amount: Int,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
)
