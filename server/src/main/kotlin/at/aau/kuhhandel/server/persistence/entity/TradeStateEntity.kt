package at.aau.kuhhandel.server.persistence.entity

import at.aau.kuhhandel.shared.enums.AnimalType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

/**
 * 1:1 child of [GameEntity] for an in-flight trade. The two offer payloads are stored as JSON
 * strings (cross-DB compatible — TEXT on Postgres / H2) and contain the chosen money-card values.
 */
@Entity
@Table(name = "trade_state")
class TradeStateEntity(
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "game_id")
    var game: GameEntity,
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenger_id", nullable = false)
    var challenger: GamePlayerEntity,
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "defender_id", nullable = false)
    var defender: GamePlayerEntity,
    @Enumerated(EnumType.STRING)
    @Column(name = "animal_type", nullable = false, length = 16)
    var animalType: AnimalType,
    @Column(name = "challenger_offer_json", columnDefinition = "TEXT")
    var challengerOfferJson: String? = null,
    @Column(name = "defender_offer_json", columnDefinition = "TEXT")
    var defenderOfferJson: String? = null,
    @Id
    @Column(name = "game_id")
    var gameId: Long? = null,
)
