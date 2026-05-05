package at.aau.kuhhandel.server.event

import at.aau.kuhhandel.shared.model.GameState

data class GameStateChangedEvent(
    val gameId: String,
    val newState: GameState,
    val requestId: String? = null,
)
