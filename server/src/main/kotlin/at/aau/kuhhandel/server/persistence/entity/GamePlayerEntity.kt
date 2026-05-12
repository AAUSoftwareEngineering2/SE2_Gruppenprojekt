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
    name = "game_players",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_game_players_seat", columnNames = ["game_id", "seat_order"]),
        UniqueConstraint(name = "uk_game_players_user", columnNames = ["game_id", "user_id"]),
    ],
)
class GamePlayerEntity(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    var game: GameEntity,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: UserEntity,
    @Column(name = "seat_order", nullable = false)
    var seatOrder: Int,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
)
