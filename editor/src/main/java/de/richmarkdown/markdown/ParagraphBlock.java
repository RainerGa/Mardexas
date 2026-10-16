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
 * Normaler Textabsatz mit einer geordneten Folge formatierter Inline-Läufe.
 *
 * <p>Inline-Elemente bleiben getrennt, damit der Writer Textformatierungen
 * und Links ohne Verlust serialisieren kann. Die Liste wird bei Erzeugung
 * defensiv kopiert.
 */
public class ParagraphBlock extends Block {

    private final List<Inline> inlines;

    /**
     * Erstellt einen Absatz.
     *
     * @param inlines formatierte Textläufe in ihrer Dokumentreihenfolge
     */
    public ParagraphBlock(List<Inline> inlines) {
        this.inlines = List.copyOf(inlines);
    }

    /**
     * Liefert die unveränderliche Inline-Folge des Absatzes.
     *
     * @return Inline-Elemente in ihrer Textreihenfolge
     */
    public List<Inline> getInlines() {
        return inlines;
    }
}
