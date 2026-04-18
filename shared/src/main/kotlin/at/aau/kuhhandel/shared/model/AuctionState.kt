package at.aau.kuhhandel.shared.model

data class AuctionState(
    // The card that is currently being auctioned
    val auctionCard: AnimalCard,
    // Current highest bid
    val highestBid: Int = 0,
    // ID of the player with the highest bid
    val highestBidderId: String? = null,
    // Can later be used for countdown / closing logic
    val isClosed: Boolean = false,
)
