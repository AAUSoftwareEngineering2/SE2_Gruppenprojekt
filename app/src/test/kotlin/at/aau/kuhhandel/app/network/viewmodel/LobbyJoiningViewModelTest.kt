package at.aau.kuhhandel.app.network.viewmodel

import at.aau.kuhhandel.app.network.game.GameRepository
import at.aau.kuhhandel.app.network.game.GameRepositoryState
import at.aau.kuhhandel.app.ui.menu.joining.LobbyJoiningViewModel
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
    fun `initial state is empty`() = runTest {
        val uiState = viewModel.uiState.value
        assertEquals("", uiState.lobbyCode)
        assertFalse(uiState.isLoading)
        assertNull(uiState.errorMessage)
        assertFalse(uiState.isJoined)
    }

    @Test
    fun `onLobbyCodeChanged updates code if valid`() = runTest {
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

    @Test
    fun `onLobbyCodeChanged ignores invalid code`() = runTest {
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

    @Test
    fun `joinLobby sets error if code is too short`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onLobbyCodeChanged("123")
        viewModel.joinLobby()
        advanceUntilIdle()

        assertEquals("Code must have 5 digits", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `joinLobby calls repository if code is 5 digits`() = runTest {
        viewModel.onLobbyCodeChanged("12345")
        viewModel.joinLobby()
        advanceUntilIdle()

        // Based on current implementation using createGame as placeholder
        coVerify { mockRepository.createGame() }
    }

    @Test
    fun `state reflects joined lobby`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        repoStateFlow.value = GameRepositoryState(gameId = "54321")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isJoined)
        assertEquals("54321", viewModel.uiState.value.joinedLobbyCode)
    }

    @Test
    fun `clearError clears both local and repository errors`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        // Trigger local error
        viewModel.onLobbyCodeChanged("1")
        viewModel.joinLobby()
        advanceUntilIdle()
        assertEquals("Code must have 5 digits", viewModel.uiState.value.errorMessage)

        viewModel.clearError()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)
        verify { mockRepository.clearError() }
    }
}
