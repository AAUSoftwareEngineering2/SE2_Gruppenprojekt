package at.aau.kuhhandel.server.model

import at.aau.kuhhandel.shared.model.GameState

// Rückgabe von reconnectPlayer: das neue (rotierte) Token + der aktuelle Spielzustand.
data class ReconnectResult(
    val reconnectToken: String,
    val gameState: GameState,
)
