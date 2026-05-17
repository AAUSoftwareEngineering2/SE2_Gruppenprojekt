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

        return cards.first() to copy(cards = cards.drop(1))
    }

    fun isEmpty(): Boolean = cards.isEmpty()

    fun size(): Int = cards.size
}
