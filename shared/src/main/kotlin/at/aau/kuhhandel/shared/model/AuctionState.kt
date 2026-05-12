package at.aau.kuhhandel.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class AuctionState(
    // The card that is currently being auctioned
    val auctionCard: AnimalCard,
    // Player who started the auction and is not allowed to bid
    val auctioneerId: String,
    // Current highest bid
    val highestBid: Int = 0,
    // ID of the player with the highest bid
    val highestBidderId: String? = null,
    // Can later be used for countdown / closing logic
    val isClosed: Boolean = false,
    // Epoch milliseconds when the auction timer expires
    val timerEndTime: Long? = null,
)
