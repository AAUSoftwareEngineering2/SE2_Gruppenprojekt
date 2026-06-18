package at.aau.kuhhandel.server.persistence.entity

import at.aau.kuhhandel.shared.enums.GamePhase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version

@Entity
@Table(
    name = "games",
    // The timeout/spy sweeps poll these columns on every tick; index them so the frequent
    // polling is a cheap index range scan instead of a full table scan.
    indexes = [
        Index(name = "idx_games_timer_end", columnList = "timer_end"),
        Index(name = "idx_games_earliest_spy_expiry", columnList = "earliest_spy_expiry"),
    ],
)
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
    // Last time a real player acted on this game (create / join / gameplay). Timeout sweeps do
    // NOT bump it, so an abandoned game goes stale and the StaleGameReaper can purge it instead
    // of the sweeper advancing it forever. Nullable so ddl-auto can add the column to old rows.
    @Column(name = "last_activity_at")
    var lastActivityAt: Long? = System.currentTimeMillis(),
    // Spy state (cheating feature) persisted so it survives the stateless save/reload round-trip
    // and is shared across pods. activeSpies expire by time -> earliestSpyExpiry lets the sweeper
    // find games to clear, mirroring timer_end for phase timeouts.
    @Column(name = "active_spies_json", columnDefinition = "text")
    var activeSpiesJson: String? = null,
    @Column(name = "spied_this_turn_json", columnDefinition = "text")
    var spiedThisTurnJson: String? = null,
    @Column(name = "earliest_spy_expiry")
    var earliestSpyExpiry: Long? = null,
    @Version
    @Column(name = "version", nullable = false)
    var version: Int = 0,
)
