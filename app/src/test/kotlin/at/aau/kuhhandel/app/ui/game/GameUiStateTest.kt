package at.aau.kuhhandel.app.ui.game

import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.MoneyCard
import at.aau.kuhhandel.shared.model.TradeState
import kotlin.test.Test
import kotlin.test.assertEquals

class GameUiStateTest {
    @Test
    fun `trade table counts use submitted money cards`() {
        val uiState =
            GameUiState(
                gameState =
                    GameState(
                        tradeState =
                            tradeState(
                                offeredMoneyCards =
                                    List(9) { index ->
                                        MoneyCard("offer-$index", 10)
                                    }.toSet(),
                                counterOfferedMoneyCards =
                                    List(3) { index ->
                                        MoneyCard("counter-$index", 50)
                                    }.toSet(),
                            ),
                    ),
            )

        assertEquals(9, uiState.tradeOfferCardCount)
        assertEquals(3, uiState.tradeCounterOfferCardCount)
    }

    @Test
    fun `trade table counts support legacy submitted card ids`() {
        val uiState =
            GameUiState(
                gameState =
                    GameState(
                        tradeState =
                            tradeState(
                                offeredMoneyCardIds = setOf("offer-1", "offer-2"),
                                counterOfferedMoneyCardIds =
                                    setOf(
                                        "counter-1",
                                        "counter-2",
                                        "counter-3",
                                        "counter-4",
                                    ),
                            ),
                    ),
            )

        assertEquals(2, uiState.tradeOfferCardCount)
        assertEquals(4, uiState.tradeCounterOfferCardCount)
    }

    private fun tradeState(
        offeredMoneyCardIds: Set<String> = emptySet(),
        counterOfferedMoneyCardIds: Set<String> = emptySet(),
        offeredMoneyCards: Set<MoneyCard>? = null,
        counterOfferedMoneyCards: Set<MoneyCard>? = null,
    ) = TradeState(
        initiatorId = "initiator",
        targetId = "target",
        requestedAnimalType = AnimalType.COW,
        offeredMoneyCardIds = offeredMoneyCardIds,
        counterOfferedMoneyCardIds = counterOfferedMoneyCardIds,
        offeredMoneyCards = offeredMoneyCards,
        counterOfferedMoneyCards = counterOfferedMoneyCards,
    )
}
