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
    name = "deck_cards",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_deck_cards_order", columnNames = ["game_id", "draw_order"]),
    ],
)
class DeckCardEntity(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    var game: GameEntity,
    @Enumerated(EnumType.STRING)
    @Column(name = "animal_type", nullable = false, length = 16)
    var animalType: AnimalType,
    @Column(name = "draw_order", nullable = false)
    var drawOrder: Int,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
)
