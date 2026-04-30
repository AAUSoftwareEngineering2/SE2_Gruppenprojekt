package at.aau.kuhhandel.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class AnimalDeck(
    val cards: List<AnimalCard> = emptyList(),
) {
    fun drawTopCard(): Pair<AnimalCard?, AnimalDeck> {
        if (cards.isEmpty()) {
            return null to this
        }

        return cards.last() to copy(cards = cards.dropLast(1))
    }

    fun isEmpty(): Boolean = cards.isEmpty()

    fun size(): Int = cards.size
}
