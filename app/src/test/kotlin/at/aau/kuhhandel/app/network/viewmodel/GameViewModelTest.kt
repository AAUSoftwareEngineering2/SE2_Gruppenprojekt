package at.aau.kuhhandel.app.network.viewmodel

import at.aau.kuhhandel.app.network.game.GameRepository
import at.aau.kuhhandel.app.network.game.GameRepositoryState
import at.aau.kuhhandel.app.ui.game.GameViewModel
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.AnimalDeck
import at.aau.kuhhandel.shared.model.AuctionState
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.Player
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockRepository = mockk<GameRepository>(relaxed = true)
    private val repoStateFlow = MutableStateFlow(GameRepositoryState())
    private val mockTimeProvider =
        object : at.aau.kuhhandel.app.ui.game.TimeProvider {
            override fun currentTimeMillis(): Long = testDispatcher.scheduler.currentTime
        }

    private lateinit var viewModel: GameViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockRepository.state } returns repoStateFlow
        viewModel = GameViewModel(mockRepository, testScope, mockTimeProvider)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() {
        runTest {
            val uiState = viewModel.uiState.value
            assertEquals(GamePhase.NOT_STARTED, uiState.currentPhase)
            assertEquals("0", uiState.deckCountText)
            assertEquals("No card revealed", uiState.activeCardLabel)
            assertFalse(uiState.isConnected)
        }
    }

    @Test
    fun `state updates from repository correctly`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            val deck = AnimalDeck(listOf(AnimalCard("1", AnimalType.COW)))
            val gameState =
                GameState(
                    phase = GamePhase.PLAYER_CHOICE,
                    deck = deck,
                    currentFaceUpCard = AnimalCard("2", AnimalType.PIG),
                )

            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = true,
                    gameState = gameState,
                )

            advanceUntilIdle()

            val uiState = viewModel.uiState.value
            assertEquals(GamePhase.PLAYER_CHOICE, uiState.currentPhase)
            assertEquals("1", uiState.deckCountText)
            assertEquals("PIG (#2)", uiState.activeCardLabel)
            assertTrue(uiState.isConnected)
            assertTrue(uiState.canRevealCard)
            assertFalse(uiState.canStartGame)
        }
    }

    @Test
    fun `canRevealCard depends on connection and phase`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            // Case 1: Connected + PLAYER_TURN + My Turn -> True
            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = true,
                    myPlayerId = "me",
                    gameState =
                        GameState(
                            phase = GamePhase.PLAYER_CHOICE,
                            players = listOf(Player(id = "me", name = "Me")),
                            currentPlayerIndex = 0,
                        ),
                )
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.canRevealCard)

            // Case 2: Connected + AUCTION -> False
            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = true,
                    gameState = GameState(phase = GamePhase.AUCTION_BIDDING),
                )
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.canRevealCard)

            // Case 3: Disconnected + PLAYER_TURN -> False
            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = false,
                    gameState = GameState(phase = GamePhase.PLAYER_CHOICE),
                )
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.canRevealCard)
        }
    }

    @Test
    fun `isAuctioneer logic works correctly`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            // Case 1: GameState null
            repoStateFlow.value = GameRepositoryState(gameState = null, myPlayerId = "me")
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isAuctioneer)

            // Case 2: AuctionState null
            repoStateFlow.value =
                GameRepositoryState(
                    myPlayerId = "me",
                    gameState = GameState(auctionState = null),
                )
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isAuctioneer)

            // Case 3: I am the auctioneer
            repoStateFlow.value =
                GameRepositoryState(
                    myPlayerId = "me",
                    gameState =
                        GameState(
                            auctionState =
                                AuctionState(
                                    auctioneerId = "me",
                                    auctionCard = AnimalCard("1", AnimalType.COW),
                                    timerEndTime = 0,
                                ),
                        ),
                )
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isAuctioneer)

            // Case 4: Someone else is the auctioneer
            repoStateFlow.value =
                GameRepositoryState(
                    myPlayerId = "me",
                    gameState =
                        GameState(
                            auctionState =
                                AuctionState(
                                    auctioneerId = "other",
                                    auctionCard = AnimalCard("1", AnimalType.COW),
                                    timerEndTime = 0,
                                ),
                        ),
                )
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isAuctioneer)
        }
    }

    @Test
    fun `canRevealCard edge cases`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            // gameState is null
            repoStateFlow.value = GameRepositoryState(isConnected = true, gameState = null)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.canRevealCard)

            // currentPlayerIndex is out of bounds
            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = true,
                    myPlayerId = "me",
                    gameState =
                        GameState(
                            phase = GamePhase.PLAYER_CHOICE,
                            players = listOf(Player("me", "Me")),
                            currentPlayerIndex = 5,
                        ),
                )
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.canRevealCard)

            // myPlayerId is null
            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = true,
                    myPlayerId = null,
                    gameState =
                        GameState(
                            phase = GamePhase.PLAYER_CHOICE,
                            players = listOf(Player("me", "Me")),
                            currentPlayerIndex = 0,
                        ),
                )
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.canRevealCard)
        }
    }

    @Test
    fun `myMoneyCards edge cases`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            // gameState is null
            repoStateFlow.value = GameRepositoryState(gameState = null)
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.myMoneyCards
                    .isEmpty(),
            )

            // players is empty
            repoStateFlow.value =
                GameRepositoryState(
                    myPlayerId = "me",
                    gameState = GameState(players = emptyList()),
                )
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.myMoneyCards
                    .isEmpty(),
            )

            // player not found
            repoStateFlow.value =
                GameRepositoryState(
                    myPlayerId = "me",
                    gameState = GameState(players = listOf(Player("other", "Other"))),
                )
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.myMoneyCards
                    .isEmpty(),
            )
        }
    }

    @Test
    fun `auction timer handles expired endTime immediately`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            // Set endTime to be in the past
            val endTime = testScheduler.currentTime - 1000
            val gameState =
                GameState(
                    phase = GamePhase.AUCTION_BIDDING,
                    auctionState =
                        AuctionState(
                            auctioneerId = "p1",
                            auctionCard = AnimalCard("1", AnimalType.COW),
                            timerEndTime = endTime,
                        ),
                )

            repoStateFlow.value = GameRepositoryState(isConnected = true, gameState = gameState)
            advanceTimeBy(1)
            assertEquals(0, viewModel.uiState.value.auctionTimerSeconds)
        }
    }

    @Test
    fun `sharedAnimals calculation edge cases`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            // Target ID is null
            viewModel.selectTargetPlayer(null)
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.sharedAnimalsWithSelectedPlayer
                    .isEmpty(),
            )

            // GameState is null
            repoStateFlow.value = GameRepositoryState(gameState = null, myPlayerId = "me")
            viewModel.selectTargetPlayer("other")
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.sharedAnimalsWithSelectedPlayer
                    .isEmpty(),
            )

            // My Player ID is null
            repoStateFlow.value = GameRepositoryState(gameState = GameState(), myPlayerId = null)
            viewModel.selectTargetPlayer("other")
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.sharedAnimalsWithSelectedPlayer
                    .isEmpty(),
            )

            // Target player not found
            repoStateFlow.value =
                GameRepositoryState(
                    myPlayerId = "me",
                    gameState = GameState(players = listOf(Player("me", "Me"))),
                )
            viewModel.selectTargetPlayer("other")
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.sharedAnimalsWithSelectedPlayer
                    .isEmpty(),
            )
        }
    }

    @Test
    fun `respondToTrade initiator branch handles nulls`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            // Both initiatorId and myPlayerId are the same string
            repoStateFlow.value =
                GameRepositoryState(
                    myPlayerId = "same",
                    gameState =
                        GameState(
                            tradeState =
                                at.aau.kuhhandel.shared.model.TradeState(
                                    initiatorId = "same",
                                    targetId = "target",
                                    requestedAnimalType = AnimalType.COW,
                                ),
                        ),
                )
            advanceUntilIdle()
            viewModel.respondToTrade()
            advanceUntilIdle()
            // Should go to initiator branch (commented out)
            coVerify(exactly = 0) { mockRepository.respondToTrade(any()) }
        }
    }

    @Test
    fun `canStartGame logic works correctly`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            // Case 1: Connected + NOT_STARTED + 3 Players + Host -> True
            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = true,
                    myPlayerId = "host-id",
                    gameState =
                        GameState(
                            phase = GamePhase.NOT_STARTED,
                            players =
                                listOf(
                                    Player("host-id", "Host"),
                                    Player("p2", "P2"),
                                    Player("p3", "P3"),
                                ),
                            hostPlayerId = "host-id",
                        ),
                )
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.canStartGame)

            // Case 2: Connected + NOT_STARTED + 3 Players + NOT Host -> False
            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = true,
                    myPlayerId = "p2",
                    gameState =
                        GameState(
                            phase = GamePhase.NOT_STARTED,
                            players =
                                listOf(
                                    Player("host-id", "Host"),
                                    Player("p2", "P2"),
                                    Player("p3", "P3"),
                                ),
                            hostPlayerId = "host-id",
                        ),
                )
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.canStartGame)

            // Case 3: Connected + NOT_STARTED + 2 Players + Host -> False
            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = true,
                    myPlayerId = "host-id",
                    gameState =
                        GameState(
                            phase = GamePhase.NOT_STARTED,
                            players =
                                listOf(
                                    Player("host-id", "Host"),
                                    Player("p2", "P2"),
                                ),
                            hostPlayerId = "host-id",
                        ),
                )
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.canStartGame)

            // Case 4: Connected + PLAYER_TURN -> False
            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = true,
                    gameState = GameState(phase = GamePhase.PLAYER_CHOICE),
                )
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.canStartGame)
        }
    }

    @Test
    fun `startGame calls repository`() {
        runTest {
            viewModel.startGame()
            advanceUntilIdle()
            coVerify { mockRepository.startGame() }
        }
    }

    @Test
    fun `revealCard calls repository`() {
        runTest {
            viewModel.revealCard()
            advanceUntilIdle()
            coVerify { mockRepository.revealCard() }
        }
    }

    @Test
    fun `placeBid calls repository and handles error`() {
        runTest {
            coEvery { mockRepository.placeBid(50) } throws RuntimeException("Network error")
            viewModel.placeBid(50)
            advanceUntilIdle()
            coVerify { mockRepository.placeBid(50) }
        }
    }

    @Test
    fun `respondToTrade calls repository with selected money and clears selection`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            // Setup: We are NOT the initiator, so we are responding
            repoStateFlow.value =
                GameRepositoryState(
                    myPlayerId = "me",
                    gameState =
                        GameState(
                            tradeState =
                                at.aau.kuhhandel.shared.model.TradeState(
                                    initiatorId = "other",
                                    targetId = "me",
                                    requestedAnimalType = AnimalType.COW,
                                ),
                        ),
                )
            advanceUntilIdle()

            viewModel.toggleMoneyCardSelection("m1")
            viewModel.toggleMoneyCardSelection("m2")
            advanceUntilIdle()

            viewModel.respondToTrade()
            advanceUntilIdle()

            coVerify { mockRepository.respondToTrade(setOf("m1", "m2")) }
            assertTrue(
                viewModel.uiState.value.selectedMoneyCardIds
                    .isEmpty(),
            )
        }
    }

    @Test
    fun `respondToTrade does not call repository when I am the initiator`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            // Setup: We ARE the initiator
            repoStateFlow.value =
                GameRepositoryState(
                    myPlayerId = "me",
                    gameState =
                        GameState(
                            tradeState =
                                at.aau.kuhhandel.shared.model.TradeState(
                                    initiatorId = "me",
                                    targetId = "other",
                                    requestedAnimalType = AnimalType.COW,
                                ),
                        ),
                )
            advanceUntilIdle()

            viewModel.toggleMoneyCardSelection("m1")
            viewModel.respondToTrade()
            advanceUntilIdle()

            // Should not call respondToTrade (since we're the initiator and the offerTrade call is currently commented out in ViewModel)
            coVerify(exactly = 0) { mockRepository.respondToTrade(any()) }
            assertTrue(
                viewModel.uiState.value.selectedMoneyCardIds
                    .isEmpty(),
            )
        }
    }

    @Test
    fun `respondToTrade handles null gameState or tradeState gracefully`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            // Case 1: GameState is null
            repoStateFlow.value = GameRepositoryState(gameState = null)
            advanceUntilIdle()
            viewModel.respondToTrade()
            advanceUntilIdle()
            // In ViewModel: if (uiState.value.gameState?.tradeState?.initiatorId == uiState.value.myPlayerId)
            // if gameState is null, the condition is null == myPlayerId (false if myPlayerId is set, true if both are null)
            // Wait, uiState.value.myPlayerId is also null initially. So null == null is TRUE.
            // If it's TRUE, it currently does nothing (initiator branch is commented out).

            coVerify(exactly = 0) { mockRepository.respondToTrade(any()) }

            // Case 2: TradeState is null
            repoStateFlow.value =
                GameRepositoryState(myPlayerId = "me", gameState = GameState(tradeState = null))
            advanceUntilIdle()
            viewModel.respondToTrade()
            advanceUntilIdle()
            // uiState.value.gameState?.tradeState?.initiatorId is null.
            // myPlayerId is "me".
            // null == "me" is FALSE.
            // So it calls repository.respondToTrade
            coVerify(exactly = 1) { mockRepository.respondToTrade(any()) }
        }
    }

    @Test
    fun `initiateTrade calls repository with target and selected money`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            viewModel.toggleMoneyCardSelection("m1")
            viewModel.selectTargetPlayer("p2")
            advanceUntilIdle()

            viewModel.initiateTrade("p2", AnimalType.COW)
            advanceUntilIdle()

            coVerify { mockRepository.initiateTrade("p2", AnimalType.COW, setOf("m1")) }
            assertTrue(
                viewModel.uiState.value.selectedMoneyCardIds
                    .isEmpty(),
            )
            assertNull(viewModel.uiState.value.selectedTargetPlayerId)
        }
    }

    @Test
    fun `finishTradeReveal calls repository`() {
        runTest {
            viewModel.finishTradeReveal()
            advanceUntilIdle()
            coVerify { mockRepository.finishTradeReveal() }
        }
    }

    @Test
    fun `money card selection logic works correctly`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            viewModel.toggleMoneyCardSelection("m1")
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.selectedMoneyCardIds
                    .contains("m1"),
            )

            viewModel.toggleMoneyCardSelection("m1")
            advanceUntilIdle()
            assertFalse(
                viewModel.uiState.value.selectedMoneyCardIds
                    .contains("m1"),
            )

            viewModel.toggleMoneyCardSelection("m2")
            advanceUntilIdle()
            viewModel.clearSelection()
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.selectedMoneyCardIds
                    .isEmpty(),
            )
        }
    }

    @Test
    fun `sharedAnimals calculation in uiState works correctly`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            val me =
                Player(
                    id = "me",
                    name = "Me",
                    animals =
                        listOf(
                            AnimalCard("1", AnimalType.COW),
                            AnimalCard("2", AnimalType.PIG),
                        ),
                )
            val other =
                Player(
                    id = "other",
                    name = "Other",
                    animals =
                        listOf(
                            AnimalCard("3", AnimalType.COW),
                            AnimalCard("4", AnimalType.HORSE),
                        ),
                )

            repoStateFlow.value =
                GameRepositoryState(
                    myPlayerId = "me",
                    gameState = GameState(players = listOf(me, other)),
                )

            viewModel.selectTargetPlayer("other")
            advanceUntilIdle()

            val shared = viewModel.uiState.value.sharedAnimalsWithSelectedPlayer
            assertEquals(1, shared.size)
            assertEquals(AnimalType.COW, shared[0])
        }
    }

    @Test
    fun `buyBack calls repository and handles error`() {
        runTest {
            coEvery { mockRepository.buyBack(true) } throws RuntimeException("Network error")
            viewModel.buyBack(true)
            advanceUntilIdle()
            coVerify { mockRepository.buyBack(true) }
        }
    }

    @Test
    fun `initiateTrade calls repository and handles error`() {
        runTest {
            coEvery { mockRepository.initiateTrade("player-2", AnimalType.COW, any()) } throws
                RuntimeException("Network error")
            viewModel.initiateTrade("player-2", AnimalType.COW)
            advanceUntilIdle()
            coVerify { mockRepository.initiateTrade("player-2", AnimalType.COW, any()) }
        }
    }

    @Test
    fun `auction timer updates correctly`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            // Set endTime to be 5 seconds in the future relative to current scheduler time
            val endTime = testScheduler.currentTime + 5000
            val gameState =
                GameState(
                    phase = GamePhase.AUCTION_BIDDING,
                    auctionState =
                        AuctionState(
                            auctioneerId = "p1",
                            auctionCard = AnimalCard("1", AnimalType.COW),
                            timerEndTime = endTime,
                        ),
                    players = listOf(Player(id = "p1", name = "P1")),
                )

            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = true,
                    gameState = gameState,
                )

            // No advanceUntilIdle() here as it might loop through the 250ms delays
            advanceTimeBy(1) // Trigger initial emission

            // Check initial timer value
            assertEquals(5, viewModel.uiState.value.auctionTimerSeconds)

            // Advance time and check again
            advanceTimeBy(2000)
            assertEquals(3, viewModel.uiState.value.auctionTimerSeconds)

            // Advance past end time
            advanceTimeBy(3000)
            assertEquals(0, viewModel.uiState.value.auctionTimerSeconds)
        }
    }

    @Test
    fun `auction timer handles extreme clock desync`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            // Simulate server clock being 40 seconds ahead (endTime appears to be 45s in future)
            val endTime = testScheduler.currentTime + 45000
            val gameState =
                GameState(
                    phase = GamePhase.AUCTION_BIDDING,
                    auctionState =
                        AuctionState(
                            auctioneerId = "p1",
                            auctionCard = AnimalCard("1", AnimalType.COW),
                            timerEndTime = endTime,
                        ),
                    players = listOf(Player(id = "p1", name = "P1")),
                )

            repoStateFlow.value =
                GameRepositoryState(
                    isConnected = true,
                    gameState = gameState,
                )

            advanceTimeBy(1)

            // UI should clamp the value to 5 seconds
            assertEquals(5, viewModel.uiState.value.auctionTimerSeconds)

            // Advance time and check local countdown
            advanceTimeBy(1000)
            assertEquals(4, viewModel.uiState.value.auctionTimerSeconds)

            advanceTimeBy(4000)
            assertEquals(0, viewModel.uiState.value.auctionTimerSeconds)
        }
    }

    @Test
    fun `auction timer is null when no auction is active`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            repoStateFlow.value =
                GameRepositoryState(
                    gameState = GameState(phase = GamePhase.PLAYER_CHOICE),
                )
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.auctionTimerSeconds)
        }
    }
}
