package at.aau.kuhhandel.app.network.viewmodel

import at.aau.kuhhandel.app.network.game.GameRepository
import at.aau.kuhhandel.app.network.game.GameRepositoryState
import at.aau.kuhhandel.app.ui.menu.joining.LobbyJoiningViewModel
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.GameStateView
import at.aau.kuhhandel.shared.model.Player
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LobbyJoiningViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockRepository = mockk<GameRepository>(relaxed = true)
    private val repoStateFlow = MutableStateFlow(GameRepositoryState())

    private lateinit var viewModel: LobbyJoiningViewModel

    private fun GameRepositoryState(
        isConnecting: Boolean = false,
        isConnected: Boolean = false,
        isReconnecting: Boolean = false,
        reconnectAttempt: Int = 0,
        gameId: String? = null,
        myPlayerId: String? = null,
        gameState: GameState? = null,
        gameStateView: GameStateView? = gameState?.toView(myPlayerId ?: "me"),
        errorMessage: String? = null,
    ): GameRepositoryState =
        at.aau.kuhhandel.app.network.game.GameRepositoryState(
            isConnecting = isConnecting,
            isConnected = isConnected,
            isReconnecting = isReconnecting,
            reconnectAttempt = reconnectAttempt,
            gameId = gameId,
            myPlayerId = myPlayerId,
            gameStateView = gameStateView,
            errorMessage = errorMessage,
        )

    private fun GameState.toView(playerId: String): GameStateView {
        val playersWithLocal =
            if (players.any { it.id == playerId }) {
                players
            } else {
                listOf(Player(playerId, playerId)) + players
            }

        return copy(
            players = playersWithLocal,
            hostPlayerId = hostPlayerId ?: playersWithLocal.first().id,
        ).createViewForPlayer(playerId)
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockRepository.state } returns repoStateFlow
        viewModel = LobbyJoiningViewModel(mockRepository, testScope)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is empty`() {
        runTest {
            val uiState = viewModel.uiState.value
            assertEquals("", uiState.lobbyCode)
            assertEquals("", uiState.playerName)
            assertNull(uiState.playerNameError)
            assertFalse(uiState.isLoading)
            assertNull(uiState.errorMessage)
            assertFalse(uiState.isJoined)
            assertFalse(uiState.canSubmit)
        }
    }

    @Test
    fun `onLobbyCodeChanged updates code if valid`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            viewModel.onLobbyCodeChanged("123")
            advanceUntilIdle()
            assertEquals("123", viewModel.uiState.value.lobbyCode)

            viewModel.onLobbyCodeChanged("12345")
            advanceUntilIdle()
            assertEquals("12345", viewModel.uiState.value.lobbyCode)
        }
    }

    @Test
    fun `onLobbyCodeChanged ignores invalid code`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            viewModel.onLobbyCodeChanged("123")
            advanceUntilIdle()

            viewModel.onLobbyCodeChanged("123456") // Too long
            advanceUntilIdle()
            assertEquals("123", viewModel.uiState.value.lobbyCode)

            viewModel.onLobbyCodeChanged("12a") // Not digits
            advanceUntilIdle()
            assertEquals("123", viewModel.uiState.value.lobbyCode)
        }
    }

    @Test
    fun `onPlayerNameChanged accepts valid name and rejects too-long input`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            viewModel.onPlayerNameChanged("Felix01")
            advanceUntilIdle()
            assertEquals("Felix01", viewModel.uiState.value.playerName)

            viewModel.onPlayerNameChanged("Felix0123") // 9 chars — ignored
            advanceUntilIdle()
            assertEquals("Felix01", viewModel.uiState.value.playerName)
        }
    }

    @Test
    fun `joinLobby sets error if code is too short`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            viewModel.onLobbyCodeChanged("123")
            viewModel.onPlayerNameChanged("Felix01")
            viewModel.joinLobby()
            advanceUntilIdle()

            assertEquals("Code must have 5 digits", viewModel.uiState.value.errorMessage)
            coVerify(exactly = 0) { mockRepository.joinGame(any(), any()) }
        }
    }

    @Test
    fun `joinLobby sets player name error if name invalid`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            viewModel.onLobbyCodeChanged("12345")
            // No name provided
            viewModel.joinLobby()
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.playerNameError)
            coVerify(exactly = 0) { mockRepository.joinGame(any(), any()) }
        }
    }

    @Test
    fun `joinLobby calls repository with code and player name`() {
        runTest {
            viewModel.onLobbyCodeChanged("12345")
            viewModel.onPlayerNameChanged("Felix01")
            viewModel.joinLobby()
            advanceUntilIdle()

            coVerify { mockRepository.joinGame("12345", "Felix01") }
        }
    }

    @Test
    fun `state reflects joined lobby`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            repoStateFlow.value = GameRepositoryState(gameId = "54321", gameState = GameState())
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isJoined)
            assertEquals("54321", viewModel.uiState.value.joinedLobbyCode)
        }
    }

    @Test
    fun `clearError clears both local and repository errors`() {
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }

            // Trigger local error
            viewModel.onLobbyCodeChanged("1")
            viewModel.onPlayerNameChanged("Felix01")
            viewModel.joinLobby()
            advanceUntilIdle()
            assertEquals("Code must have 5 digits", viewModel.uiState.value.errorMessage)

            viewModel.clearError()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.errorMessage)
            verify { mockRepository.clearError() }
        }
    }
}
