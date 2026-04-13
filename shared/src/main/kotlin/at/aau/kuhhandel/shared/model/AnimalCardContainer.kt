package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.AnimalType

/**
 * Container für die Tierkarten eines Spielers.
 * Maximal 4 Karten, minimal 0.
 */
class AnimalCardContainer {
    private val cards = mutableListOf<AnimalCard>()

    /**
     * Fügt eine Tierkarte hinzu, wenn das Limit von 4 noch nicht erreicht ist.
     * @return true, wenn die Karte hinzugefügt wurde, sonst false.
     */
    fun addCard(card: AnimalCard): Boolean =
        if (cards.size < 4) {
            cards.add(card)
            true
        } else {
            false
        }

    /**
     * Entfernt eine Tierkarte des angegebenen Typs (die erste, die gefunden wird).
     * @return die entfernte Karte oder null, wenn keine Karte dieses Typs vorhanden ist.
     */
    fun removeCard(type: AnimalType): AnimalCard? {
        val index = cards.indexOfFirst { it.type == type }
        return if (index != -1) {
            cards.removeAt(index)
        } else {
            null
        }
    }

    /**
     * Gibt alle aktuell enthaltenen Tierkarten als unveränderliche Liste zurück.
     */
    fun getAllCards(): List<AnimalCard> = cards.toList()

    /**
     * Zählt, wie viele Karten eines bestimmten Typs im Container sind.
     */
    fun getCountByType(type: AnimalType): Int = cards.count { it.type == type }

    /**
     * Berechnet die Anzahl der vollständigen Quartette (4 gleiche Karten).
     * Beispiel: 4 Kühe → 1 Quartett, 8 Kühe → 2 Quartette (wenn Limit nicht wäre).
     * Da das Limit bei 4 liegt, ist das Ergebnis immer 0 oder 1.
     */
    fun getFullQuartetCount(): Int {
        val typeCounts = cards.groupingBy { it.type }.eachCount()
        return typeCounts.values.sumOf { it / 4 }
    }

    /**
     * Gibt die aktuelle Anzahl der Karten im Container zurück.
     */
    fun numberOfCards(): Int = cards.size

    /**
     * Prüft, ob der Container leer ist.
     */
    fun isEmpty(): Boolean = cards.isEmpty()
}
