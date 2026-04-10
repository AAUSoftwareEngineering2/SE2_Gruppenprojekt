package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.GamePhase

data class GameState(
    val phase: GamePhase = GamePhase.NOT_STARTED,
    val deck: AnimalDeck = AnimalDeck(),
    val currentFaceUpCard: AnimalCard? = null,
    val currentPlayerIndex: Int = 0,
    val players: List<PlayerState> = emptyList(),
)
