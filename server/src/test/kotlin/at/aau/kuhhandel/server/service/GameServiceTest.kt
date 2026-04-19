package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.shared.enums.GamePhase
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GameServiceTest {
    @Test
    fun test_createGame_generatesFiveDigitCode() {
        val service = GameService()

        val session = service.createGame("player-1")

        assertNotNull(session)
        assertEquals(5, session.gameId.length)
        assertEquals(GamePhase.NOT_STARTED, session.gameState.phase)
    }

    @Test
    fun test_createGame_generatesDifferentCodes() {
        val service = GameService()

        val firstSession = service.createGame("player-1")
        val secondSession = service.createGame("player-1")

        assertNotEquals(firstSession.gameId, secondSession.gameId)
    }

    @Test
    fun test_getGame_returnsCorrectSession() {
        val service = GameService()
        val session = service.createGame("player-1")

        val loadedSession = service.getGame(session.gameId)

        assertNotNull(loadedSession)
        assertEquals(session.gameId, loadedSession.gameId)
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
        val session = service.createGame("player-1")

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
        val session = service.createGame("player-1")
        val gameId = session.gameId

        assertNotNull(service.getGame(gameId))

        service.removeGame(gameId)

        assertNull(service.getGame(gameId))
    }

    @Test
    fun test_revealNextCard_updatesGameState() {
        val service = GameService()
        val session = service.createGame("player-1")
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
        val session = service.createGame("player-1")
        service.startGame(session.gameId)

        service.revealNextCard(session.gameId)
        service.revealNextCard(session.gameId)
        service.revealNextCard(session.gameId)
        val finalState = service.revealNextCard(session.gameId)

        assertNotNull(finalState)
        assertEquals(GamePhase.FINISHED, finalState.phase)
        assertNull(finalState.currentFaceUpCard)
    }
}
