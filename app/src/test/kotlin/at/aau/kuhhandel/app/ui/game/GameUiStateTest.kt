package at.aau.kuhhandel.app.ui.game

import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
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

    @Test
    fun `trade result cards are ordered and totals are calculated`() {
        val uiState =
            GameUiState(
                gameState =
                    GameState(
                        tradeState =
                            tradeState(
                                offeredMoneyCards =
                                    setOf(
                                        MoneyCard("offer-100", 100),
                                        MoneyCard("offer-10-b", 10),
                                        MoneyCard("offer-10-a", 10),
                                    ),
                                counterOfferedMoneyCards =
                                    setOf(
                                        MoneyCard("counter-200", 200),
                                        MoneyCard("counter-0", 0),
                                    ),
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
                gameState =
                    GameState(
                        tradeState =
                            tradeState(
                                offeredMoneyCards = setOf(MoneyCard("offer-50", 50)),
                                counterOfferedMoneyCards = emptySet(),
                            ),
                    ),
                currentPhase = GamePhase.TRADE_RESULT,
            )

        assertEquals(emptyList(), uiState.tradeResultCounterOfferCards)
        assertEquals(0, uiState.tradeResultCounterOfferTotal)
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
