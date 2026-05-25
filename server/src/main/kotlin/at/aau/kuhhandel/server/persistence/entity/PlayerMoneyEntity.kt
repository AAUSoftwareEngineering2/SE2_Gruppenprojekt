package at.aau.kuhhandel.server.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
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
    name = "player_money",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_player_money_value", columnNames = ["player_id", "card_value"]),
    ],
)
class PlayerMoneyEntity(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    var player: GamePlayerEntity,
    @Column(name = "card_value", nullable = false)
    var cardValue: Int,
    @Column(name = "amount", nullable = false)
    var amount: Int,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
)
