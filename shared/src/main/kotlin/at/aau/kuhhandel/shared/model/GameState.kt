package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.GamePhase

// Holds the state of the current game
data class GameState (
    val phase: GamePhase = GamePhase.NOT_STARTED,
    val deck: AnimalDeck = AnimalDeck(), // Deck containing all remaining cards
    val currentFaceUpCard: AnimalCard? = null, // current card facing up, can also be null
    val currentPlayerIndex: Int = 0, // index of player who's turn it is
    val players: List<PlayerState> = emptyList() // list of the players and their currnet state (card, money etc.)
)
