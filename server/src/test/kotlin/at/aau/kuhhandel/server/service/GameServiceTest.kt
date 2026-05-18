package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.event.GameStateChangedEvent
import at.aau.kuhhandel.server.model.GameSession
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.AuctionState
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.PlayerState
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.kotlin.after
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GameServiceTest {
    private companion object {
        const val CARDS_PER_ANIMAL_TYPE = 4
        val FULL_DECK_SIZE = AnimalType.entries.size * CARDS_PER_ANIMAL_TYPE
    }

    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var gameSession: GameSession
    private lateinit var service: GameService
    private val gameStateToReturn =
        GameState(
            players = listOf(PlayerState("player-1", "Player 1")),
            hostPlayerId = "explicit string for testing",
        )

    @BeforeEach
    fun setUp() {
        eventPublisher = mock(ApplicationEventPublisher::class.java)
        gameSession = mock(GameSession::class.java)

        service = GameService(eventPublisher, { _, _, _ -> gameSession })

        whenever(gameSession.state).thenReturn(gameStateToReturn.copy(hostPlayerId = "player-1"))
    }

    @Test
    fun test_createGame_generatesFiveDigitCode() {
        // Use the default constructor which runs a real GameSession
        val service = GameService(eventPublisher)

        val result = service.createGame("Player 1")

        assertEquals(5, result.gameId.length)
        assertEquals(GamePhase.NOT_STARTED, result.gameState.phase)
    }

    @Test
    fun test_createGame_generatesDifferentCodes() {
        val firstResult = service.createGame("Player 1")
        val secondResult = service.createGame("Player 1")

        assertNotEquals(firstResult.gameId, secondResult.gameId)
    }

    @Test
    fun test_createGame_returnsCorrectResult() {
        // Use the default constructor which runs a real GameSession
        val service = GameService(eventPublisher)

        val result = service.createGame("Player 1")

        assertEquals(1, result.gameState.players.size)
        assertEquals(result.playerId, result.gameState.players[0].id)
        assertEquals("Player 1", result.gameState.players[0].name)
    }

    @Test
    fun test_getGame_returnsCorrectSession() {
        // Use the default constructor which runs a real GameSession
        val service = GameService(eventPublisher)

        val result = service.createGame("Player 1")

        val loadedSession = service.getGame(result.gameId)

        assertNotNull(loadedSession)
        assertEquals(result.gameId, loadedSession.gameId)
    }

    @Test
    fun test_getGame_returnsNull_forInvalidGameId() {
        val loadedSession = service.getGame("fake code")

        assertNull(loadedSession)
    }

    @Test
    fun test_startGame_delegatesWork() {
        val result = service.createGame("Player 1")
        whenever(gameSession.startGame(result.playerId)).thenReturn(gameStateToReturn)

        val state = service.startGame(result.gameId, result.playerId)

        verify(gameSession).startGame(result.playerId)
        assertEquals(gameStateToReturn, state)
    }

    @Test
    fun test_startGame_throws_forInvalidGameId() {
        assertThrows<IllegalStateException> {
            service.startGame("fake code", "player-1")
        }
        verify(gameSession, never()).startGame(any())
    }

    @Test
    fun test_removeGame_removesGameSession() {
        val result = service.createGame("Player 1")
        val gameId = result.gameId

        assertNotNull(service.getGame(gameId))

        service.removeGame(gameId)

        assertNull(service.getGame(gameId))
    }

    @Test
    fun test_joinGame_delegatesWork() {
        val createResult = service.createGame("Player 1")
        whenever(gameSession.addPlayer(any(), eq("Player 2"))).thenReturn(gameStateToReturn)

        val joinResult = service.joinGame(createResult.gameId, "Player 2")

        verify(gameSession).addPlayer(any(), eq("Player 2"))
        assertNotNull(joinResult)
        assertEquals(gameStateToReturn, joinResult.gameState)
    }

    @Test
    fun test_joinGame_throws_forInvalidGameId() {
        assertThrows<IllegalStateException> {
            service.joinGame("fake code", "Player 1")
        }
        verify(gameSession, never()).addPlayer(any(), any())
    }

    @Test
    fun test_leaveGame_delegatesWork() {
        val result = service.createGame("Player 1")
        whenever(gameSession.removePlayer(result.playerId)).thenReturn(gameStateToReturn)

        val state = service.leaveGame(result.gameId, result.playerId)

        verify(gameSession).removePlayer(result.playerId)
        assertEquals(gameStateToReturn, state)
    }

    @Test
    fun test_leaveGame_removesGameWhenEmpty() {
        val result = service.createGame("Player 1")
        val returnedState = gameStateToReturn.copy(players = emptyList())
        whenever(gameSession.removePlayer(result.playerId)).thenReturn(returnedState)

        val state = service.leaveGame(result.gameId, result.playerId)

        verify(gameSession).removePlayer(result.playerId)
        assertEquals(returnedState, state)
        assertNull(service.getGame(result.gameId))
    }

    @Test
    fun test_leaveGame_throws_forInvalidGameId() {
        assertThrows<IllegalStateException> {
            service.leaveGame("fake code", "player-1")
        }
        verify(gameSession, never()).removePlayer(any())
    }

    @Test
    fun test_chooseAuction_delegatesWork() {
        val result = service.createGame("Player 1")
        whenever(gameSession.chooseAuction(result.playerId)).thenReturn(gameStateToReturn)

        val state = service.chooseAuction(result.gameId, result.playerId)

        verify(gameSession).chooseAuction(result.playerId)
        assertEquals(gameStateToReturn, state)
    }

    @Test
    fun test_chooseAuction_throws_forInvalidGameId() {
        assertThrows<IllegalStateException> {
            service.chooseAuction("fake code", "player-1")
        }
        verify(gameSession, never()).chooseAuction(any())
    }

    @Test
    fun test_placeBid_delegatesWork() {
        val result = service.createGame("Player 1")
        whenever(gameSession.placeBid(result.playerId, 10)).thenReturn(gameStateToReturn)

        val state = service.placeBid(result.gameId, result.playerId, 10)

        verify(gameSession).placeBid(result.playerId, 10)
        assertEquals(gameStateToReturn, state)
    }

    @Test
    fun test_placeBid_throws_forInvalidGameId() {
        assertThrows<IllegalStateException> {
            service.placeBid("fake code", "player-1", 10)
        }
        verify(gameSession, never()).placeBid(any(), any())
    }

    @Test
    fun test_closeAuctionAfterTimeout_delegatesWork() {
        val result = service.createGame("Player 1")
        whenever(gameSession.closeAuctionAfterTimeout()).thenReturn(gameStateToReturn)

        val state = service.closeAuctionAfterTimeout(result.gameId)

        verify(gameSession).closeAuctionAfterTimeout()
        assertEquals(gameStateToReturn, state)
    }

    @Test
    fun test_closeAuctionAfterTimeout_returnsNull_forInvalidGameId() {
        val state = service.closeAuctionAfterTimeout("fake code")

        assertNull(state)
        verify(gameSession, never()).closeAuctionAfterTimeout()
    }

    @Test
    fun test_resolveAuction_delegatesWork() {
        val result = service.createGame("Player 1")
        whenever(
            gameSession.resolveAuction(
                result.playerId,
                auctioneerBuysCard = false,
            ),
        ).thenReturn(gameStateToReturn)

        val state =
            service.resolveAuction(result.gameId, result.playerId, auctioneerBuysCard = false)

        verify(gameSession).resolveAuction(result.playerId, auctioneerBuysCard = false)
        assertEquals(gameStateToReturn, state)
    }

    @Test
    fun test_resolveAuction_throws_forInvalidGameId() {
        assertThrows<IllegalStateException> {
            service.resolveAuction("fake code", "player-1", auctioneerBuysCard = false)
        }
        verify(gameSession, never()).resolveAuction(any(), any())
    }

    @Test
    fun test_chooseTrade_delegatesWork() {
        val result = service.createGame("Player 1")
        whenever(
            gameSession.chooseTrade(
                result.playerId,
                "player-2",
                AnimalType.COW,
                setOf(),
            ),
        ).thenReturn(gameStateToReturn)

        val state =
            service.chooseTrade(result.gameId, result.playerId, "player-2", AnimalType.COW, setOf())

        verify(gameSession).chooseTrade(result.playerId, "player-2", AnimalType.COW, setOf())
        assertEquals(gameStateToReturn, state)
    }

    @Test
    fun test_chooseTrade_throws_forInvalidGameId() {
        assertThrows<IllegalStateException> {
            service.chooseTrade("fake code", "player-1", "player-2", AnimalType.COW, setOf())
        }
        verify(gameSession, never()).chooseTrade(any(), any(), any(), any())
    }

    @Test
    fun test_respondToTrade_delegatesWork() {
        val result = service.createGame("Player 1")
        whenever(
            gameSession.respondToTrade(result.playerId, setOf()),
        ).thenReturn(gameStateToReturn)

        val state =
            service.respondToTrade(result.gameId, result.playerId, setOf())

        verify(gameSession).respondToTrade(result.playerId, setOf())
        assertEquals(gameStateToReturn, state)
    }

    @Test
    fun test_respondToTrade_throws_forInvalidGameId() {
        assertThrows<IllegalStateException> {
            service.respondToTrade("fake code", "player-1", setOf())
        }
        verify(gameSession, never()).respondToTrade(any(), any())
    }

    @Test
    fun test_scheduleAutoClose_executesAndPublishesEvent() {
        val result = service.createGame("Player 1")
        val auctionState =
            AuctionState(
                auctionCard = AnimalCard(id = "animal-card-1", AnimalType.COW),
                auctioneerId = "player-1",
                timerEndTime = 10000L,
            )

        whenever(gameSession.state).thenReturn(gameStateToReturn.copy(auctionState = auctionState))
        whenever(gameSession.closeAuctionAfterTimeout()).thenReturn(gameStateToReturn)

        service.chooseAuction(result.gameId, result.playerId)

        // The timer is set to 5 seconds. We need to wait for the coroutine.
        // In a real unit test we might want to mock the dispatcher or use runTest,
        // but since it's using CoroutineScope(Dispatchers.Default), it's harder to control.
        // For now, let's just wait a bit longer than 5.1s.
        Thread.sleep(6000)

        // We verify that an event is published, and specifically one for the auto-close.
        verify(gameSession).closeAuctionAfterTimeout()
        verify(eventPublisher, timeout(1000)).publishEvent(any<GameStateChangedEvent>())
    }

    @Test
    fun test_scheduleAutoClose_abortsIfTimerChanged() {
        val auctionState1 =
            AuctionState(
                auctionCard = AnimalCard(id = "animal-card-1", AnimalType.COW),
                auctioneerId = "player-1",
                timerEndTime = 10000L,
            )
        val auctionState2 = auctionState1.copy(timerEndTime = 28125L)

        val result = service.createGame("Player 1")
        whenever(gameSession.state).thenReturn(gameStateToReturn.copy(auctionState = auctionState1))

        service.chooseAuction(result.gameId, result.playerId)

        // Simulate a bid halfway through
        Thread.sleep(3000)
        whenever(gameSession.state).thenReturn(gameStateToReturn.copy(auctionState = auctionState2))

        // Wait for the first scheduleAutoClose to finish
        Thread.sleep(3000)

        // The auction should NOT be closed by the FIRST scheduleAutoClose
        // because the timerEndTime changed.
        // Note: the SECOND scheduleAutoClose (from placeBid) might still be running.
        verify(gameSession, never()).closeAuctionAfterTimeout()
        verify(eventPublisher, after(1000).never()).publishEvent(any<GameStateChangedEvent>())
    }

    @Test
    fun test_scheduleAutoClose_abortsIfAuctionAlreadyClosed() {
        val result = service.createGame("Player 1")
        val auctionState =
            AuctionState(
                auctionCard = AnimalCard(id = "animal-card-1", AnimalType.COW),
                auctioneerId = "player-1",
                timerEndTime = 10000L,
            )

        whenever(gameSession.state).thenReturn(gameStateToReturn.copy(auctionState = auctionState))

        service.chooseAuction(result.gameId, result.playerId)

        // Simulate auction closing
        whenever(gameSession.state).thenReturn(gameStateToReturn)

        Thread.sleep(6000)
        // Should not have published more events from scheduleAutoClose if it checks isClosed
        // No NEW events after the auction is closed
        verify(gameSession, never()).closeAuctionAfterTimeout()
        verify(eventPublisher, after(1000).never()).publishEvent(any<GameStateChangedEvent>())
    }

    @Test
    fun test_scheduleAutoClose_returnsEarlyIfSessionRemoved() {
        val result = service.createGame("Player 1")

        service.chooseAuction(result.gameId, result.playerId)

        service.removeGame(result.gameId)

        Thread.sleep(6000)
        verify(gameSession, never()).closeAuctionAfterTimeout()

        verify(eventPublisher, never()).publishEvent(any<GameStateChangedEvent>())
    }
}
