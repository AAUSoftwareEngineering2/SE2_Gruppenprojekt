package at.aau.kuhhandel.server.persistence.entity

import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
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
    @Enumerated(EnumType.STRING)
    @Column(name = "phase", length = 32)
    var phase: GamePhase? = null,
    @Column(name = "timer_end")
    var timerEnd: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "active_player_id")
    var activePlayer: UserEntity? = null,
    @Column(name = "host_player_id", length = 64)
    var hostPlayerId: String? = null,
    @Column(name = "round_number", nullable = false)
    var roundNumber: Int = 0,
    @Enumerated(EnumType.STRING)
    @Column(name = "face_up_animal_type", length = 16)
    var faceUpAnimalType: AnimalType? = null,
    // Last time a real player acted on this game (create / join / gameplay). Timeout sweeps do
    // NOT bump it, so an abandoned game goes stale and the StaleGameReaper can purge it instead
    // of the sweeper advancing it forever. Nullable so ddl-auto can add the column to old rows.
    @Column(name = "last_activity_at")
    var lastActivityAt: Long? = System.currentTimeMillis(),
    @Version
    @Column(name = "version", nullable = false)
    var version: Int = 0,
)
