package at.aau.kuhhandel.server.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "leaderboard")
class LeaderboardEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
    @Column(name = "player_name", nullable = false)
    var playerName: String,
    @Column(name = "score", nullable = false)
    var score: Int,
    @Column(name = "quartet_count", nullable = false)
    var quartetCount: Int,
    @Column(name = "total_money", nullable = false)
    var totalMoney: Int,
    @Column(name = "created_at", nullable = false)
    var createdAt: Long = System.currentTimeMillis(),
)
