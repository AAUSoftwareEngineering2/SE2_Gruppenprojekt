package at.aau.kuhhandel.app.ui.game

import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.AuctionState
import at.aau.kuhhandel.shared.model.MoneyCard
import at.aau.kuhhandel.shared.model.TradeStateView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameUiStateTest {
    @Test
    fun `trade table counts use submitted money cards`() {
        val uiState =
            GameUiState(
                tradeState =
                    tradeStateView(
                        initiatorCardCount = 9,
                        targetCardCount = 3,
                    ),
            )

        assertEquals(9, uiState.tradeOfferCardCount)
        assertEquals(3, uiState.tradeCounterOfferCardCount)
    }

    @Test
    fun `trade table counts are null before submitted money is visible`() {
        val uiState =
            GameUiState(
                tradeState = tradeStateView(),
            )

        assertEquals(null, uiState.tradeOfferCardCount)
        assertEquals(null, uiState.tradeCounterOfferCardCount)
    }

    @Test
    fun `trade result cards are ordered and totals are calculated`() {
        val uiState =
            GameUiState(
                tradeState =
                    tradeStateView(
                        visibleInitiatorCards =
                            listOf(
                                MoneyCard("offer-100", 100),
                                MoneyCard("offer-10-b", 10),
                                MoneyCard("offer-10-a", 10),
                            ),
                        visibleTargetCards =
                            listOf(
                                MoneyCard("counter-200", 200),
                                MoneyCard("counter-0", 0),
                            ),
                    ),
                currentPhase = GamePhase.TRADE_RESULT,
            )

        assertEquals(
            listOf("offer-10-a", "offer-10-b", "offer-100"),
            uiState.tradeResultOfferCards.map { it.id },
        )
        assertEquals(
            listOf("counter-0", "counter-200"),
            uiState.tradeResultCounterOfferCards.map { it.id },
        )
        assertEquals(120, uiState.tradeResultOfferTotal)
        assertEquals(200, uiState.tradeResultCounterOfferTotal)
    }

    @Test
    fun `blind acceptance has an empty counter offer with total zero`() {
        val uiState =
            GameUiState(
                tradeState =
                    tradeStateView(
                        visibleInitiatorCards = listOf(MoneyCard("offer-50", 50)),
                        visibleTargetCards = emptyList(),
                    ),
                currentPhase = GamePhase.TRADE_RESULT,
            )

        assertEquals(emptyList(), uiState.tradeResultCounterOfferCards)
        assertEquals(0, uiState.tradeResultCounterOfferTotal)
    }

    @Test
    fun `auction payment values use buyer and selected money cards`() {
        val uiState =
            GameUiState(
                auctionState = auctionPaymentState(buyerId = "me", highestBid = 15),
                myPlayerId = "me",
                currentPhase = GamePhase.AUCTION_PAYMENT,
                myMoneyCards =
                    listOf(
                        MoneyCard("m5", 5),
                        MoneyCard("m10", 10),
                        MoneyCard("m20", 20),
                    ),
                selectedMoneyCardIds = setOf("m5", "m10"),
            )

        assertTrue(uiState.isAuctionActive)
        assertTrue(uiState.isAuctionBuyer)
        assertEquals(15, uiState.auctionBidToPay)
        assertEquals(15, uiState.selectedMoneyTotal)
        assertTrue(uiState.canSubmitAuctionPayment)
        assertTrue(uiState.canAuctioneerBuyBack)
    }

    @Test
    fun `auction payment submission is disabled when selected money is too low`() {
        val uiState =
            GameUiState(
                auctionState = auctionPaymentState(buyerId = "me", highestBid = 20),
                myPlayerId = "me",
                currentPhase = GamePhase.AUCTION_PAYMENT,
                myMoneyCards =
                    listOf(
                        MoneyCard("m5", 5),
                        MoneyCard("m10", 10),
                    ),
                selectedMoneyCardIds = setOf("m10"),
            )

        assertTrue(uiState.isAuctionBuyer)
        assertEquals(10, uiState.selectedMoneyTotal)
        assertFalse(uiState.canSubmitAuctionPayment)
        assertFalse(uiState.canAuctioneerBuyBack)
    }

    @Test
    fun `auction payment buyer flag is false outside the payment phase`() {
        val uiState =
            GameUiState(
                auctionState = auctionPaymentState(buyerId = "me", highestBid = 20),
                myPlayerId = "me",
                currentPhase = GamePhase.AUCTIONEER_DECISION,
            )

        assertFalse(uiState.isAuctionBuyer)
    }

    private fun tradeStateView(
        initiatorCardCount: Int? = null,
        targetCardCount: Int? = null,
        visibleInitiatorCards: List<MoneyCard>? = null,
        visibleTargetCards: List<MoneyCard>? = null,
    ) = TradeStateView(
        initiatorId = "initiator",
        targetId = "target",
        animalCards = emptyList(),
        initiatorCardCount = initiatorCardCount,
        targetCardCount = targetCardCount,
        visibleInitiatorCards = visibleInitiatorCards,
        visibleTargetCards = visibleTargetCards,
    )

    private fun auctionPaymentState(
        buyerId: String,
        highestBid: Int,
    ) = AuctionState(
        auctionCard = AnimalCard("cow-1", AnimalType.COW),
        auctioneerId = "seller",
        highestBid = highestBid,
        highestBidderId = buyerId,
        buyerId = buyerId,
        sellerId = "seller",
    )
}
