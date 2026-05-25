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
 * 1:1 child of [GameEntity] holding the snapshot of an in-flight auction. The PK is shared with
 * the owning game (`@MapsId`), which mirrors the diagram's `game_id : BIGINT «PK, FK»`.
 *
 * Volatile fields like the auctioneer's id, the auction timer, and the `isClosed` flag stay in the
 * in-memory [at.aau.kuhhandel.shared.model.AuctionState] for the prototype and are not persisted.
 */
@Entity
@Table(name = "auction_state")
class AuctionStateEntity(
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "game_id")
    var game: GameEntity,
    @Enumerated(EnumType.STRING)
    @Column(name = "current_animal", nullable = false, length = 16)
    var currentAnimal: AnimalType,
    @Column(name = "highest_bid", nullable = false)
    var highestBid: Int = 0,
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "highest_bidder_id")
    var highestBidder: GamePlayerEntity? = null,
    @Column(name = "passed_players", columnDefinition = "TEXT")
    var passedPlayersJson: String? = null,
    @Column(name = "timer_end_time")
    var timerEndTime: Long? = null,
    @Column(name = "is_closed", nullable = false)
    var isClosed: Boolean = false,
    @Id
    @Column(name = "game_id")
    var gameId: Long? = null,
)
