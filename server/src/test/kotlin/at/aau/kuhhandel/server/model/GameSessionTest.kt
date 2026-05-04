package at.aau.kuhhandel.server.model

import at.aau.kuhhandel.server.service.GameCommand
import at.aau.kuhhandel.server.service.GameStateMachine
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.GameState
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.Mockito.`when` as whenever

class GameSessionTest {
    @Test
    fun test_newSession_isNotStarted() {
        val session = GameSession("12345", "player-1")

        assertEquals("12345", session.gameId)
        assertEquals(GamePhase.NOT_STARTED, session.gameState.phase)
        assertTrue(session.gameState.deck.isEmpty())
        assertNull(session.gameState.currentFaceUpCard)
        assertEquals(0, session.gameState.currentPlayerIndex)
        assertEquals(1, session.gameState.players.size)
        assertEquals(
            "player-1",
            session.gameState.players[0]
                .id,
        )
        assertNull(session.gameState.auctionState)
        assertNull(session.gameState.tradeState)
    }

    @Test
    fun test_startGame_initializesGame() {
        val session = GameSession("12345", "player-1")

        val state = session.startGame()

        assertEquals(GamePhase.PLAYER_TURN, state.phase)
        assertEquals(3, state.deck.size())
        assertNull(state.currentFaceUpCard)
        assertEquals(0, state.currentPlayerIndex)
        assertEquals(1, session.gameState.players.size)
        assertEquals(
            "player-1",
            session.gameState.players[0]
                .id,
        )
        assertNull(state.auctionState)
        assertNull(state.tradeState)
    }

    @Test
    fun test_startGame_updatesStoredState() {
        val session = GameSession("12345", "player-1")

        session.startGame()

        assertEquals(GamePhase.PLAYER_TURN, session.gameState.phase)
        assertEquals(3, session.gameState.deck.size())
        assertNull(session.gameState.currentFaceUpCard)
    }

    @Test
    fun test_revealNextCard_revealsCard() {
        val session = GameSession("12345", "player-1")
        session.startGame()

        val state = session.revealNextCard()

        assertNotNull(state.currentFaceUpCard)
        assertEquals(2, state.deck.size())
        assertEquals(GamePhase.PLAYER_TURN, state.phase)
        assertNull(state.auctionState)
        assertNull(state.tradeState)
    }

    @Test
    fun test_revealNextCard_updatesStoredState() {
        val session = GameSession("12345", "player-1")
        session.startGame()

        session.revealNextCard()

        assertNotNull(session.gameState.currentFaceUpCard)
        assertEquals(2, session.gameState.deck.size())
        assertEquals(GamePhase.PLAYER_TURN, session.gameState.phase)
    }

    @Test
    fun test_revealNextCard_lastCardDoesNotImmediatelyFinishGame() {
        val session = GameSession("12345", "player-1")
        session.startGame()

        session.revealNextCard()
        session.revealNextCard()
        val stateAfterLastCard = session.revealNextCard()

        assertNotNull(stateAfterLastCard.currentFaceUpCard)
        assertEquals(0, stateAfterLastCard.deck.size())
        assertEquals(GamePhase.PLAYER_TURN, stateAfterLastCard.phase)
    }

    @Test
    fun test_revealNextCard_finishesGame_whenDeckAlreadyEmpty() {
        val session = GameSession("12345", "player-1")
        session.startGame()

        session.revealNextCard()
        session.revealNextCard()
        session.revealNextCard()
        val finalState = session.revealNextCard()

        assertEquals(GamePhase.FINISHED, finalState.phase)
        assertNull(finalState.currentFaceUpCard)
        assertNull(finalState.auctionState)
        assertNull(finalState.tradeState)
    }

    @Test
    fun test_chooseAuction_updatesStoredState() {
        val session = GameSession("12345", "player-1")
        session.startGame()

        val state = session.chooseAuction()

        assertEquals(GamePhase.AUCTION, state.phase)
        assertNotNull(state.auctionState)
        assertEquals(2, state.deck.size())
        assertNull(state.currentFaceUpCard)
        assertEquals(GamePhase.AUCTION, session.gameState.phase)
    }

    @Test
    fun test_chooseTrade_rejectsUnknownPlayer() {
        val session = GameSession("12345", "player-1")
        session.startGame()

        assertFailsWith<IllegalArgumentException> {
            session.chooseTrade("player-2")
        }
    }

    @Test
    fun test_offerTrade_rejectsWhenNotInTradePhase() {
        val session = GameSession("12345", "player-1")
        session.startGame()

        assertFailsWith<IllegalStateException> {
            session.offerTrade(listOf("m-1"))
        }
    }

    @Test
    fun test_respondToTrade_rejectsWhenNotInTradePhase() {
        val session = GameSession("12345", "player-1")
        session.startGame()

        assertFailsWith<IllegalStateException> {
            session.respondToTrade("player-2", true)
        }
    }

    @Test
    fun test_offerTrade_rejectsBeforeGameStarted() {
        val session = GameSession("12345", "player-1")

        assertFailsWith<IllegalStateException> {
            session.offerTrade(listOf("m-1"))
        }
    }

    @Test
    fun test_respondToTrade_rejectsBeforeGameStarted() {
        val session = GameSession("12345", "player-1")

        assertFailsWith<IllegalStateException> {
            session.respondToTrade("player-2", false)
        }
    }

    @Test
    fun test_offerTrade_returnsAndStoresStateFromStateMachine() {
        val mockMachine = mock(GameStateMachine::class.java)
        val expected = GameState(phase = GamePhase.TRADE)
        whenever(mockMachine.apply(any(), any<GameCommand.OfferTrade>())).thenReturn(expected)

        val session = GameSession("12345", "player-1", mockMachine)
        val result = session.offerTrade(listOf("m-1"))

        assertEquals(expected, result)
        assertEquals(expected, session.gameState)
    }

    @Test
    fun test_respondToTrade_returnsAndStoresStateFromStateMachine() {
        val mockMachine = mock(GameStateMachine::class.java)
        val expected = GameState(phase = GamePhase.ROUND_END)
        whenever(
            mockMachine.apply(any(), eq(GameCommand.RespondToTrade("player-2", true))),
        ).thenReturn(expected)

        val session = GameSession("12345", "player-1", mockMachine)
        val result = session.respondToTrade("player-2", true)

        assertEquals(expected, result)
        assertEquals(expected, session.gameState)
    }

    @Test
    fun test_finishRound_updatesStoredState() {
        val session = GameSession("12345", "player-1")
        session.startGame()
        session.chooseAuction()

        val state = session.finishRound()

        assertEquals(GamePhase.PLAYER_TURN, state.phase)
        assertEquals(2, state.roundNumber)
        assertNull(state.auctionState)
        assertNull(state.tradeState)
        assertEquals(state, session.gameState)
    }
}
