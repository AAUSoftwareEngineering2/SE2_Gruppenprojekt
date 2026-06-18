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
    // IDs of players who bluffed and are excluded from bidding in this auction
    val excludedPlayerIds: Set<String> = emptySet(),
    // The ID of the player who must pay for and receives the auctioned card
    val buyerId: String? = null,
    // The ID of the player who receives the payment for the auctioned card
    val sellerId: String? = null,
)
