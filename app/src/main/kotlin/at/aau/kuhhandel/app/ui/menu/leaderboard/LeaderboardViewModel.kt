package at.aau.kuhhandel.app.ui.menu.leaderboard

import at.aau.kuhhandel.app.network.leaderboard.LeaderboardService
import at.aau.kuhhandel.shared.model.GlobalLeaderboardItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LeaderboardUiState(
    val items: List<GlobalLeaderboardItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class LeaderboardViewModel(
    private val service: LeaderboardService,
    private val scope: CoroutineScope,
) {
    private val leaderboardItems = MutableStateFlow<List<GlobalLeaderboardItem>>(emptyList())
    private val isLoading = MutableStateFlow(true)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LeaderboardUiState> =
        combine(
            leaderboardItems,
            isLoading,
            errorMessage,
        ) { items, loading, error ->
            LeaderboardUiState(
                items = items,
                isLoading = loading,
                errorMessage = error,
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LeaderboardUiState(isLoading = true),
        )

    init {
        loadLeaderboard()
    }

    /** Triggers a network fetch to retrieve up-to-date leaderboard rankings. */
    fun loadLeaderboard() {
        isLoading.value = true
        errorMessage.value = null
        scope.launch {
            service
                .fetchTopScores()
                .onSuccess { items ->
                    leaderboardItems.value = items
                    isLoading.value = false
                }.onFailure { error ->
                    errorMessage.value = error.localizedMessage ?: "Network Error"
                    isLoading.value = false
                }
        }
    }
}
