package de.richmarkdown.markdown;

import java.util.List;

/**
 * Einzelner Listenpunkt einer Aufzählungs- oder nummerierten Liste.
 *
 * <p>Die Ebene ist einsbasiert und beschreibt die Verschachtelungstiefe; das
 * Modell gruppiert Listeneinträge bewusst nicht in separate Listencontainer.
 * Aufzählungsart und Inline-Inhalt werden pro Punkt gehalten.
 */
public class ListItemBlock extends Block {

    private final int level;
    private final boolean numbered;
    private final List<Inline> inlines;

    /**
     * Erstellt einen Listenpunkt.
     *
     * @param level einsbasierte Verschachtelungstiefe
     * @param numbered {@code true} für nummerierte Liste, sonst Aufzählung
     * @param inlines formatierter Inhalt des Listenpunkts
     * @throws IllegalArgumentException wenn {@code level} kleiner als 1 ist
     */
    public ListItemBlock(int level, boolean numbered, List<Inline> inlines) {
        if (level < 1) {
            throw new IllegalArgumentException("Invalid list level: " + level);
        }
        this.level = level;
        this.numbered = numbered;
        this.inlines = List.copyOf(inlines);
    }

    /**
     * Liefert die Verschachtelungsebene des Listenpunkts.
     *
     * @return einsbasierte Verschachtelungstiefe
     */
    public int getLevel() {
        return level;
    }

    /**
     * Gibt an, ob der Punkt zu einer nummerierten Liste gehört.
     *
     * @return {@code true}, wenn der Punkt nummeriert ist
     */
    public boolean isNumbered() {
        return numbered;
    }

    /**
     * Liefert den formatierten Inhalt des Listenpunkts.
     *
     * @return formatierte Inline-Elemente in Textreihenfolge
     */
    public List<Inline> getInlines() {
        return inlines;
    }
}
