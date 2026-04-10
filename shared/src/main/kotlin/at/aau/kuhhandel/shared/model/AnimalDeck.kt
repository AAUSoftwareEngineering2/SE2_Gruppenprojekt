package at.aau.kuhhandel.shared.model

class AnimalDeck(
    val cards: MutableList<AnimalCard> = mutableListOf(),
) {
    fun drawTopCard(): AnimalCard? {
        if (cards.isEmpty()) {
            return null
        }

        return cards.removeAt(cards.lastIndex)
    }

    fun isEmpty(): Boolean = cards.isEmpty()

    fun size(): Int = cards.size
}
