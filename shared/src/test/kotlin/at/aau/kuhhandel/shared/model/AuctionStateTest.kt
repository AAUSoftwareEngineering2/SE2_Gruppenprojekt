package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.AnimalType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AuctionStateTest {
    @Test
    fun test_defaultAuctionState() {
        val card = AnimalCard(id = "1", type = AnimalType.COW)
        val state = AuctionState(auctionCard = card)

        assertEquals(card, state.auctionCard)
        assertEquals(0, state.highestBid)
        assertNull(state.highestBidderId)
        assertFalse(state.isClosed)
    }
}
