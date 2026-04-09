package at.aau.kuhhandel.shared.model

data class AnimalDeck (
    val cards: MutableList<AnimalCard> = mutableListOf()
) {
    fun drawTopCard(): AnimalCard? {
        if (cards.isEmpty()) return null
        return cards.removeAt(0) // card drawing function, returns null if empty, removes the first card
    }

    fun isEmpty(): Boolean = cards.isEmpty()

    fun size(): Int = cards.size
}
