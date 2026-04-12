package at.aau.kuhhandel.server.model

import at.aau.kuhhandel.shared.model.GameState

/**
 * One running game session on the server
 *
 * ID to identify the game (maybe not as neccesary in our case)
 * Has the current GameState from the shared module
 */
data class GameSession(
    val sessionId: String,
    val gameState: GameState,
)
