@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.event.GameStateChangedEvent
import at.aau.kuhhandel.server.exception.GameException
import at.aau.kuhhandel.server.model.GameSession
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GameErrorReason
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.AuctionState
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.Player
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
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
            players = listOf(Player("player-1", "Player 1")),
            hostPlayerId = "explicit string for testing",
        )

    @BeforeEach
    fun setUp() {
        eventPublisher = mock(ApplicationEventPublisher::class.java)
        gameSession = mock(GameSession::class.java)

        service = GameService(eventPublisher, gameSessionFactory = { _, _, _ -> gameSession })

        whenever(gameSession.state).thenReturn(gameStateToReturn)
    }

    @Test
    fun test_createGame_generatesFiveDigitCode() =
        runTest {
            // Use the default constructor which runs a real GameSession
            val service = GameService(eventPublisher)

            val result = service.createGame("Player 1")

            assertEquals(5, result.gameId.length)
            assertEquals(GamePhase.NOT_STARTED, result.gameState.phase)
        }

    @Test
    fun test_createGame_generatesDifferentCodes() =
        runTest {
            val firstResult = service.createGame("Player 1")
            val secondResult = service.createGame("Player 1")

            assertNotEquals(firstResult.gameId, secondResult.gameId)
        }

    @Test
    fun test_createGame_returnsCorrectResult() =
        runTest {
            // Use the default constructor which runs a real GameSession
            val service = GameService(eventPublisher)

            val result = service.createGame("Player 1")

            assertEquals(1, result.gameState.players.size)
            assertEquals(result.playerId, result.gameState.players[0].id)
            assertEquals("Player 1", result.gameState.players[0].name)
        }

    @Test
    fun test_getGame_returnsCorrectSession() =
        runTest {
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
    fun test_startGame_delegatesWork() =
        runTest {
            val result = service.createGame("Player 1")
            whenever(gameSession.startGame(result.playerId)).thenReturn(gameStateToReturn)

            val state = service.startGame(result.gameId, result.playerId)

            verify(gameSession).startGame(result.playerId)
            assertEquals(gameStateToReturn, state)
        }

    @Test
    fun test_startGame_throws_forInvalidGameId() =
        runTest {
            assertThrows<IllegalStateException> {
                service.startGame("fake code", "player-1")
            }
            verify(gameSession, never()).startGame(any())
        }

    @Test
    fun test_removeGame_removesGameSession() =
        runTest {
            val result = service.createGame("Player 1")
            val gameId = result.gameId

            assertNotNull(service.getGame(gameId))

            service.removeGame(gameId)

            assertNull(service.getGame(gameId))
        }

    @Test
    fun test_joinGame_delegatesWork() =
        runTest {
            val createResult = service.createGame("Player 1")
            whenever(gameSession.addPlayer(any(), eq("Player 2"))).thenReturn(gameStateToReturn)

            val joinResult = service.joinGame(createResult.gameId, "Player 2")

            verify(gameSession).addPlayer(any(), eq("Player 2"))
            assertNotNull(joinResult)
            assertEquals(gameStateToReturn, joinResult.gameState)
        }

    @Test
    fun test_joinGame_throws_forInvalidGameId() =
        runTest {
            val exception =
                assertThrows<GameException> {
                    service.getStateForReconnection("fake code", "player-1")
                }
            assertEquals(GameErrorReason.GAME_NOT_FOUND, exception.reason)
            verify(gameSession, never()).addPlayer(any(), any())
        }

    @Test
    fun test_leaveGame_delegatesWork() =
        runTest {
            val result = service.createGame("Player 1")
            whenever(gameSession.removePlayer(result.playerId)).thenReturn(gameStateToReturn)

            val state = service.leaveGame(result.gameId, result.playerId)

            verify(gameSession).removePlayer(result.playerId)
            assertEquals(gameStateToReturn, state)
        }

    @Test
    fun test_leaveGame_removesGameWhenEmpty() =
        runTest {
            val result = service.createGame("Player 1")
            val returnedState = gameStateToReturn.copy(players = emptyList())
            whenever(gameSession.removePlayer(result.playerId)).thenReturn(returnedState)

            val state = service.leaveGame(result.gameId, result.playerId)

            verify(gameSession).removePlayer(result.playerId)
            assertEquals(returnedState, state)
            assertNull(service.getGame(result.gameId))
        }

    @Test
    fun test_leaveGame_throws_forInvalidGameId() =
        runTest {
            assertThrows<IllegalStateException> {
                service.leaveGame("fake code", "player-1")
            }
            verify(gameSession, never()).removePlayer(any())
        }

    @Test
    fun test_getStateForReconnection_returnsState() =
        runTest {
            val result = service.createGame("Player 1")
            whenever(gameSession.hasPlayer(result.playerId)).thenReturn(true)

            val state = service.getStateForReconnection(result.gameId, result.playerId)

            verify(gameSession).hasPlayer(result.playerId)
            assertEquals(gameStateToReturn, state)
        }

    @Test
    fun test_getStateForReconnection_throws_forInvalidGameId() =
        runTest {
            val exception =
                assertThrows<GameException> {
                    service.getStateForReconnection("fake code", "player-1")
                }
            assertEquals(GameErrorReason.GAME_NOT_FOUND, exception.reason)
        }

    @Test
    fun test_getStateForReconnection_throws_forInvalidPlayerId() =
        runTest {
            val result = service.createGame("Player 1")
            whenever(gameSession.hasPlayer("player-2")).thenReturn(false)

            val exception =
                assertThrows<GameException> {
                    service.getStateForReconnection(result.gameId, "player-2")
                }
            assertEquals(GameErrorReason.PLAYER_NOT_IN_GAME, exception.reason)
        }

    @Test
    fun test_chooseAuction_delegatesWork() =
        runTest {
            val result = service.createGame("Player 1")
            whenever(gameSession.chooseAuction(result.playerId)).thenReturn(gameStateToReturn)

            val state = service.chooseAuction(result.gameId, result.playerId)

            verify(gameSession).chooseAuction(result.playerId)
            assertEquals(gameStateToReturn, state)
        }

    @Test
    fun test_chooseAuction_throws_forInvalidGameId() =
        runTest {
            assertThrows<IllegalStateException> {
                service.chooseAuction("fake code", "player-1")
            }
            verify(gameSession, never()).chooseAuction(any())
        }

    @Test
    fun test_placeBid_delegatesWork() =
        runTest {
            val result = service.createGame("Player 1")
            whenever(gameSession.placeBid(result.playerId, 10)).thenReturn(gameStateToReturn)

            val state = service.placeBid(result.gameId, result.playerId, 10)

            verify(gameSession).placeBid(result.playerId, 10)
            assertEquals(gameStateToReturn, state)
        }

    @Test
    fun test_placeBid_throws_forInvalidGameId() =
        runTest {
            assertThrows<IllegalStateException> {
                service.placeBid("fake code", "player-1", 10)
            }
            verify(gameSession, never()).placeBid(any(), any())
        }

    @Test
    fun test_resolveAuction_delegatesWork() =
        runTest {
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
    fun test_resolveAuction_schedulesAutoClose_whenBluffRestartsAuction() =
        runTest {
            service =
                GameService(
                    eventPublisher = eventPublisher,
                    gameSessionFactory = { _, _, _ -> gameSession },
                    serviceScope = backgroundScope,
                )

            val result = service.createGame("Player 1")
            val now = System.currentTimeMillis()
            val targetTimerEnd = now + 5000L

            val restartedAuctionState =
                AuctionState(
                    auctionCard = AnimalCard(id = "animal-card-1", AnimalType.COW),
                    auctioneerId = result.playerId,
                    timerEndTime = targetTimerEnd,
                    excludedPlayerIds = setOf("player-2"),
                )
            val restartedGameState =
                gameStateToReturn.copy(
                    phase = GamePhase.AUCTION_BIDDING,
                    timerEnd = targetTimerEnd,
                    auctionState = restartedAuctionState,
                )
            val closedGameState =
                restartedGameState.copy(
                    phase = GamePhase.AUCTIONEER_DECISION,
                    timerEnd = null,
                    auctionState = restartedAuctionState.copy(timerEndTime = null),
                )

            whenever(
                gameSession.resolveAuction(
                    result.playerId,
                    auctioneerBuysCard = false,
                ),
            ).thenReturn(restartedGameState)
            whenever(gameSession.state).thenReturn(restartedGameState)
            whenever(gameSession.closeAuctionAfterTimeout()).thenReturn(closedGameState)

            val state =
                service.resolveAuction(result.gameId, result.playerId, auctioneerBuysCard = false)

            // Advance past the 5000ms delay + 100ms safety pad
            advanceTimeBy(5200)

            assertEquals(restartedGameState, state)
            verify(gameSession).closeAuctionAfterTimeout()
            verify(eventPublisher, timeout(1000)).publishEvent(any<GameStateChangedEvent>())
        }

    @Test
    fun test_resolveAuction_throws_forInvalidGameId() =
        runTest {
            assertThrows<IllegalStateException> {
                service.resolveAuction("fake code", "player-1", auctioneerBuysCard = false)
            }
            verify(gameSession, never()).resolveAuction(any(), any())
        }

    @Test
    fun test_chooseTrade_delegatesWork() =
        runTest {
            val result = service.createGame("Player 1")
            whenever(
                gameSession.chooseTrade(
                    result.playerId,
                    "player-2",
                    AnimalType.COW,
                ),
            ).thenReturn(gameStateToReturn)

            val state =
                service.chooseTrade(
                    result.gameId,
                    result.playerId,
                    "player-2",
                    AnimalType.COW,
                )

            verify(gameSession).chooseTrade(result.playerId, "player-2", AnimalType.COW)
            assertEquals(gameStateToReturn, state)
        }

    @Test
    fun test_chooseTrade_throws_forInvalidGameId() =
        runTest {
            assertThrows<IllegalStateException> {
                service.chooseTrade("fake code", "player-1", "player-2", AnimalType.COW)
            }
            verify(gameSession, never()).chooseTrade(any(), any(), any())
        }

    @Test
    fun test_submitTradeMoney_delegatesWork() =
        runTest {
            val result = service.createGame("Player 1")
            whenever(
                gameSession.submitTradeMoney(
                    result.playerId,
                    emptySet(),
                ),
            ).thenReturn(gameStateToReturn)

            val state =
                service.submitTradeMoney(
                    result.gameId,
                    result.playerId,
                    emptySet(),
                )

            verify(gameSession).submitTradeMoney(result.playerId, emptySet())
            assertEquals(gameStateToReturn, state)
        }

    @Test
    fun test_submitTradeMoney_throws_forInvalidGameId() =
        runTest {
            assertThrows<IllegalStateException> {
                service.submitTradeMoney("fake code", "player-1", emptySet())
            }
            verify(gameSession, never()).chooseTrade(any(), any(), any())
        }

    @Test
    fun test_respondToTrade_delegatesWork() =
        runTest {
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
    fun test_respondToTrade_throws_forInvalidGameId() =
        runTest {
            assertThrows<IllegalStateException> {
                service.respondToTrade("fake code", "player-1", setOf())
            }
            verify(gameSession, never()).respondToTrade(any(), any())
        }

    @Test
    fun test_schedulePhaseTimeout_executesAndPublishesEvent() =
        runTest {
            service =
                GameService(
                    eventPublisher = eventPublisher,
                    gameSessionFactory = { _, _, _ -> gameSession },
                    serviceScope = backgroundScope,
                )

            val result = service.createGame("Player 1")
            val now = System.currentTimeMillis()
            val targetTimerEnd = now + 5000L

            val auctionState =
                AuctionState(
                    auctionCard = AnimalCard(id = "animal-card-1", AnimalType.COW),
                    auctioneerId = "player-1",
                    timerEndTime = targetTimerEnd,
                )
            val activeGameState =
                gameStateToReturn.copy(
                    phase = GamePhase.AUCTION_BIDDING,
                    timerEnd = targetTimerEnd,
                    auctionState = auctionState,
                )

            whenever(gameSession.chooseAuction(result.playerId)).thenReturn(activeGameState)
            whenever(gameSession.state).thenAnswer { activeGameState }
            whenever(gameSession.closeAuctionAfterTimeout()).thenReturn(
                gameStateToReturn.copy(
                    timerEnd = null,
                ),
            )

            service.chooseAuction(result.gameId, result.playerId)

            // Advance time to trigger the coroutine
            advanceTimeBy(5200)

            // We verify that an event is published, and specifically one for the auto-close.
            verify(gameSession).closeAuctionAfterTimeout()
            verify(eventPublisher, timeout(1000)).publishEvent(any<GameStateChangedEvent>())
        }

    @Test
    fun test_schedulePhaseTimeout_abortsIfTimerChanged() =
        runTest {
            service =
                GameService(
                    eventPublisher = eventPublisher,
                    gameSessionFactory = { _, _, _ -> gameSession },
                    serviceScope = backgroundScope,
                )

            val result = service.createGame("Player 1")
            val now = System.currentTimeMillis()
            val initialTimerEnd = now + 5000L
            val updatedTimerEnd = now + 10000L

            val auctionState1 =
                AuctionState(
                    auctionCard = AnimalCard(id = "animal-card-1", AnimalType.COW),
                    auctioneerId = "player-1",
                    timerEndTime = initialTimerEnd,
                )
            val state1 =
                gameStateToReturn.copy(
                    phase = GamePhase.AUCTION_BIDDING,
                    timerEnd = initialTimerEnd,
                    auctionState = auctionState1,
                )

            whenever(gameSession.chooseAuction(result.playerId)).thenReturn(state1)

            // Use a mutable variable to simulate the state changing mid-flight
            var currentMockedState = state1
            whenever(gameSession.state).thenAnswer { currentMockedState }

            service.chooseAuction(result.gameId, result.playerId)

            // Simulate a new bid coming in halfway through the countdown (at 3 seconds)
            advanceTimeBy(3000)

            val auctionState2 = auctionState1.copy(timerEndTime = updatedTimerEnd)
            currentMockedState =
                state1.copy(
                    timerEnd = updatedTimerEnd, // The timer has changed
                    auctionState = auctionState2,
                )

            // Pass the remaining 2200ms of the first timer
            advanceTimeBy(2200)

            // The first timeout job should skip execution because the timer end updated
            verify(gameSession, never()).closeAuctionAfterTimeout()
            verify(eventPublisher, never()).publishEvent(any<GameStateChangedEvent>())
        }

    @Test
    fun test_schedulePhaseTimeout_abortsIfAuctionAlreadyClosed() =
        runTest {
            service =
                GameService(
                    eventPublisher = eventPublisher,
                    gameSessionFactory = { _, _, _ -> gameSession },
                    serviceScope = backgroundScope,
                )

            val result = service.createGame("Player 1")
            val now = System.currentTimeMillis()
            val targetTimerEnd = now + 5000L

            val auctionState =
                AuctionState(
                    auctionCard = AnimalCard(id = "animal-card-1", AnimalType.COW),
                    auctioneerId = "player-1",
                    timerEndTime = targetTimerEnd,
                )
            val activeGameState =
                gameStateToReturn.copy(
                    phase = GamePhase.AUCTION_BIDDING,
                    timerEnd = targetTimerEnd,
                    auctionState = auctionState,
                )

            whenever(gameSession.chooseAuction(result.playerId)).thenReturn(activeGameState)

            var currentMockedState = activeGameState
            whenever(gameSession.state).thenAnswer { currentMockedState }

            service.chooseAuction(result.gameId, result.playerId)

            // Simulate closing the auction early
            currentMockedState =
                gameStateToReturn.copy(
                    phase = GamePhase.AUCTIONEER_DECISION,
                    timerEnd = null, // The timer was cleared
                    auctionState = null,
                )

            advanceTimeBy(5200)

            // The background timer should abort since the timer end is now null
            verify(gameSession, never()).closeAuctionAfterTimeout()
            verify(eventPublisher, never()).publishEvent(any<GameStateChangedEvent>())
        }

    @Test
    fun test_schedulePhaseTimeout_returnsEarlyIfSessionRemoved() =
        runTest {
            service =
                GameService(
                    eventPublisher = eventPublisher,
                    gameSessionFactory = { _, _, _ -> gameSession },
                    serviceScope = backgroundScope,
                )

            val result = service.createGame("Player 1")
            val now = System.currentTimeMillis()
            val targetTimerEnd = now + 5000L

            val activeGameState =
                gameStateToReturn.copy(
                    phase = GamePhase.AUCTION_BIDDING,
                    timerEnd = targetTimerEnd,
                )
            whenever(gameSession.chooseAuction(result.playerId)).thenReturn(activeGameState)
            whenever(gameSession.state).thenReturn(activeGameState)

            service.chooseAuction(result.gameId, result.playerId)

            // Remove the game from memory mid-flight
            service.removeGame(result.gameId)

            advanceTimeBy(5200)

            verify(gameSession, never()).closeAuctionAfterTimeout()
            verify(eventPublisher, never()).publishEvent(any<GameStateChangedEvent>())
        }
}
