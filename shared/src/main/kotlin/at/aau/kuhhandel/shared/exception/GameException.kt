package at.aau.kuhhandel.shared.exception

import at.aau.kuhhandel.shared.enums.GameErrorReason

class GameException(
    val reason: GameErrorReason,
) : RuntimeException()
