/*
 * Copyright (C) 2026 Rainer Garbotz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.richmarkdown.markdown;

import java.util.List;

/**
 * Einzelner Listenpunkt einer Aufzählungs- oder nummerierten Liste.
 *
 * <p>Die Ebene ist einsbasiert und beschreibt die Verschachtelungstiefe; das
 * Modell gruppiert Listeneinträge bewusst nicht in separate Listencontainer.
 * Aufzählungsart, Startnummer und Inline-Inhalt werden pro Punkt gehalten.
 */
public class ListItemBlock extends Block {

    private final int level;
    private final boolean numbered;
    private final int startNumber;
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
        this(level, numbered, 1, inlines);
    }

    /**
     * Erstellt einen Listenpunkt mit Startnummer.
     *
     * @param level einsbasierte Verschachtelungstiefe
     * @param numbered {@code true} für nummerierte Liste, sonst Aufzählung
     * @param startNumber Startnummer für nummerierte Listen (>= 1), wird bei Aufzählungen ignoriert
     * @param inlines formatierter Inhalt des Listenpunkts
     * @throws IllegalArgumentException wenn {@code level} kleiner als 1 oder {@code startNumber} kleiner als 1 ist
     */
    public ListItemBlock(int level, boolean numbered, int startNumber, List<Inline> inlines) {
        if (level < 1) {
            throw new IllegalArgumentException("Invalid list level: " + level);
        }
        if (startNumber < 1) {
            throw new IllegalArgumentException("Invalid start number: " + startNumber);
        }
        this.level = level;
        this.numbered = numbered;
        this.startNumber = startNumber;
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
     * Liefert die Startnummer für nummerierte Listen.
     *
     * @return Startnummer (>= 1), Standard ist 1
     */
    public int getStartNumber() {
        return startNumber;
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
