package at.aau.kuhhandel.shared.model

/**
 * Container für die Geldkarten eines Spielers.
 * Es gibt keine feste Obergrenze für die Anzahl der Geldkarten.
 */
class MoneyCardContainer {
    private val cards = mutableListOf<MoneyCard>()

    /**
     * Fügt eine einzelne Geldkarte hinzu.
     */
    fun addCard(card: MoneyCard) {
        cards.add(card)
    }

    /**
     * Fügt mehrere Geldkarten auf einmal hinzu.
     */
    fun addCards(cardsToAdd: List<MoneyCard>) {
        cards.addAll(cardsToAdd)
    }

    /**
     * Entfernt eine bestimmte Geldkarte (genaue Übereinstimmung anhand der ID).
     * @return true, wenn die Karte gefunden und entfernt wurde, sonst false.
     */
    fun removeCard(card: MoneyCard): Boolean {
        val index = cards.indexOfFirst { it.id == card.id }
        return if (index != -1) {
            cards.removeAt(index)
            true
        } else {
            false
        }
    }

    /**
     * Entfernt alle Geldkarten mit einem bestimmten Wert.
     * @return Liste der entfernten Karten (kann leer sein).
     */
    fun removeCardsByValue(value: Int): List<MoneyCard> {
        val removed = cards.filter { it.value == value }
        cards.removeAll { it.value == value }
        return removed
    }

    /**
     * Gibt alle aktuell enthaltenen Geldkarten als unveränderliche Liste zurück.
     */
    fun getAllCards(): List<MoneyCard> = cards.toList()

    /**
     * Berechnet den Gesamtwert aller Geldkarten im Container.
     */
    fun totalValue(): Int = cards.sumOf { it.value }

    /**
     * Gibt die aktuelle Anzahl der Geldkarten zurück.
     */
    fun size(): Int = cards.size

    /**
     * Prüft, ob der Container leer ist.
     */
    fun isEmpty(): Boolean = cards.isEmpty()
}
