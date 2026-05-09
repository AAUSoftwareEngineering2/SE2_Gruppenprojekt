package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.shared.enums.GamePhase
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GameServiceTest {
    @Test
    fun test_createGame_generatesFiveDigitCode() {
        val service = GameService()

        val result = service.createGame("Player 1")

        assertEquals(5, result.gameId.length)
        assertEquals(GamePhase.NOT_STARTED, result.gameState.phase)
    }

    @Test
    fun test_createGame_generatesDifferentCodes() {
        val service = GameService()

        val firstResult = service.createGame("Player 1")
        val secondResult = service.createGame("Player 1")

        assertNotEquals(firstResult.gameId, secondResult.gameId)
    }

    @Test
    fun test_createGame_returnsCorrectResult() {
        val service = GameService()

        val result = service.createGame("Player 1")

        assertEquals(1, result.gameState.players.size)
        assertEquals(result.playerId, result.gameState.players[0].id)
        assertEquals("Player 1", result.gameState.players[0].name)
    }

    @Test
    fun test_getGame_returnsCorrectSession() {
        val service = GameService()
        val result = service.createGame("Player 1")

        val loadedSession = service.getGame(result.gameId)

        assertNotNull(loadedSession)
        assertEquals(result.gameId, loadedSession.gameId)
    }

    @Test
    fun test_getGame_returnsNull_forInvalidGameId() {
        val service = GameService()

        val loadedSession = service.getGame("99999")

        assertNull(loadedSession)
    }

    @Test
    fun test_startGame_startsExistingGame() {
        val service = GameService()
        val session = service.createGame("Player 1")

        val state = service.startGame(session.gameId)

        assertNotNull(state)
        assertEquals(GamePhase.PLAYER_TURN, state.phase)
        assertEquals(3, state.deck.size())
        assertNull(state.currentFaceUpCard)
    }

    @Test
    fun test_startGame_returnsNull_forInvalidGameId() {
        val service = GameService()

        val state = service.startGame("99999")

        assertNull(state)
    }

    @Test
    fun test_removeGame_removesGameSession() {
        val service = GameService()
        val session = service.createGame("Player 1")
        val gameId = session.gameId

        assertNotNull(service.getGame(gameId))

        service.removeGame(gameId)

        assertNull(service.getGame(gameId))
    }

    @Test
    fun test_joinGame_updatesGameState() {
        val service = GameService()
        val initialResult = service.createGame("Player 1")

        val result = service.joinGame(initialResult.gameId, "Player 2")

        assertNotNull(result)
        assertEquals(2, result.gameState.players.size)
        assertEquals(result.playerId, result.gameState.players[1].id)
        assertEquals("Player 2", result.gameState.players[1].name)
    }

    @Test
    fun test_joinGame_returnsNull_forInvalidGameId() {
        val service = GameService()

        val result = service.joinGame("fake code", "Player 1")

        assertNull(result)
    }

    @Test
    fun test_leaveGame_updatesGameState() {
        val service = GameService()
        val session = service.createGame("Player 1")
        val result = service.joinGame(session.gameId, "Player 2")

        val state = service.leaveGame(session.gameId, result!!.playerId)

        assertNotNull(state)
        assertEquals(1, state.players.size)
        assertEquals("Player 1", state.players[0].name)
    }

    @Test
    fun test_leaveGame_returnsNull_forInvalidGameId() {
        val service = GameService()

        val state = service.leaveGame("fake code", "player-1")

        assertNull(state)
    }

    @Test
    fun test_revealNextCard_updatesGameState() {
        val service = GameService()
        val session = service.createGame("Player 1")
        service.startGame(session.gameId)

        val state = service.revealNextCard(session.gameId)

        assertNotNull(state)
        assertNotNull(state.currentFaceUpCard)
        assertEquals(GamePhase.PLAYER_TURN, state.phase)
        assertEquals(2, state.deck.size())
    }

    @Test
    fun test_revealNextCard_returnsNull_forInvalidGameId() {
        val service = GameService()

        val result = service.revealNextCard("99999")

        assertNull(result)
    }

    @Test
    fun test_revealNextCard_finishesGame_whenDeckAlreadyEmpty() {
        val service = GameService()
        val session = service.createGame("Player 1")
        service.startGame(session.gameId)

        service.revealNextCard(session.gameId)
        service.revealNextCard(session.gameId)
        service.revealNextCard(session.gameId)
        val finalState = service.revealNextCard(session.gameId)

        assertNotNull(finalState)
        assertEquals(GamePhase.FINISHED, finalState.phase)
        assertNull(finalState.currentFaceUpCard)
    }

    @Test
    fun test_chooseAuction_updatesGameState() {
        val service = GameService()
        val session = service.createGame("Player 1")
        service.startGame(session.gameId)

        val state = service.chooseAuction(session.gameId)

        assertNotNull(state)
        assertEquals(GamePhase.AUCTION, state.phase)
        assertNotNull(state.auctionState)
        assertEquals(2, state.deck.size())
        assertNull(state.currentFaceUpCard)
    }

    @Test
    fun test_chooseAuction_returnsNull_forInvalidGameId() {
        val service = GameService()

        val result = service.chooseAuction("99999")

        assertNull(result)
    }

    @Test
    fun test_placeBid_propagatesInvalidBid() {
        val service = GameService()
        val session = service.createGame("Player 1")
        service.startGame(session.gameId)
        service.chooseAuction(session.gameId)

        assertFailsWith<IllegalArgumentException> {
            service.placeBid(session.gameId, "player-2", 10)
        }
    }

    @Test
    fun test_placeBid_returnsNull_forInvalidGameId() {
        val service = GameService()

        val result = service.placeBid("99999", "player-2", 10)

        assertNull(result)
    }

    @Test
    fun test_closeAuction_returnsNull_forInvalidGameId() {
        val service = GameService()

        val result = service.closeAuction("99999")

        assertNull(result)
    }

    @Test
    fun test_resolveAuction_updatesGameState() {
        val service = GameService()
        val session = service.createGame("Player 1")
        service.startGame(session.gameId)
        service.chooseAuction(session.gameId)
        service.closeAuction(session.gameId)

        val state = service.resolveAuction(session.gameId, auctioneerBuysCard = false)

        assertNotNull(state)
        assertEquals(GamePhase.ROUND_END, state.phase)
        assertEquals(1, state.players[0].animals.size)
        assertNull(state.auctionState)
    }

    @Test
    fun test_resolveAuction_returnsNull_forInvalidGameId() {
        val service = GameService()

        val result = service.resolveAuction("99999", auctioneerBuysCard = false)

        assertNull(result)
    }

    @Test
    fun test_chooseTrade_returnsNull_forInvalidGameId() {
        val service = GameService()

        val result = service.chooseTrade("99999", "player-2")

        assertNull(result)
    }

    @Test
    fun test_chooseTrade_propagatesInvalidTrade() {
        val service = GameService()
        val session = service.createGame("Player 1")
        service.startGame(session.gameId)

        assertFailsWith<IllegalArgumentException> {
            service.chooseTrade(session.gameId, "player-2")
        }
    }

    @Test
    fun test_offerTrade_returnsNull_forInvalidGameId() {
        val service = GameService()

        val result = service.offerTrade("99999", listOf("m-1"))

        assertNull(result)
    }

    @Test
    fun test_offerTrade_propagatesInvalidPhase() {
        val service = GameService()
        val session = service.createGame("player-1")
        service.startGame(session.gameId)

        assertFailsWith<IllegalStateException> {
            service.offerTrade(session.gameId, listOf("m-1"))
        }
    }

    @Test
    fun test_respondToTrade_returnsNull_forInvalidGameId() {
        val service = GameService()

        val result = service.respondToTrade("99999", "player-2", true)

        assertNull(result)
    }

    @Test
    fun test_respondToTrade_propagatesInvalidPhase() {
        val service = GameService()
        val session = service.createGame("player-1")
        service.startGame(session.gameId)

        assertFailsWith<IllegalStateException> {
            service.respondToTrade(session.gameId, "player-2", false)
        }
    }

    @Test
    fun test_finishRound_updatesGameState() {
        val service = GameService()
        val session = service.createGame("Player 1")
        service.startGame(session.gameId)
        service.chooseAuction(session.gameId)

        val state = service.finishRound(session.gameId)

        assertNotNull(state)
        assertEquals(GamePhase.PLAYER_TURN, state.phase)
        assertEquals(2, state.roundNumber)
        assertNull(state.auctionState)
        assertNull(state.tradeState)
    }

    @Test
    fun test_finishRound_returnsNull_forInvalidGameId() {
        val service = GameService()

        val result = service.finishRound("99999")

        assertNull(result)
    }
}
