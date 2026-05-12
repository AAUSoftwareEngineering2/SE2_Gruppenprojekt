package at.aau.kuhhandel.server.persistence.entity

/**
 * High-level lifecycle of a persisted game — coarser than the in-memory [at.aau.kuhhandel.shared.enums.GamePhase].
 * A game stays in [LOBBY] while it is being set up or between rounds, and only moves into [AUCTION]
 * or [TRADE] for the duration of those interactions.
 */
enum class GameStatus {
    LOBBY,
    AUCTION,
    TRADE,
    FINISHED,
}
