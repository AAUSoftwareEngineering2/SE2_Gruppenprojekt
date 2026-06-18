package at.aau.kuhhandel.server.model

import at.aau.kuhhandel.shared.model.GameState

data class ReconnectResult(
    val reconnectToken: String,
    val gameState: GameState,
)
