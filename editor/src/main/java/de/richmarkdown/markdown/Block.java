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

/**
 * Basistyp sämtlicher blockorientierter Elemente des internen Markdown-Modells.
 *
 * <p>Konkrete Untertypen repräsentieren Absätze, Überschriften, Listen,
 * Zitate, Code-Blöcke, Tabellen, Bilder und horizontale Trennlinien. Die
 * Blockfolge eines {@link MarkdownModel} erhält die Dokumentreihenfolge und
 * wird vom {@link MarkdownWriter} in Markdown serialisiert.
 *
 * <p>Block-Objekte sind Wertträger; untergeordnete Listen werden von den
 * jeweiligen Implementierungen defensiv kopiert.
 *
 * @see MarkdownModel
 * @see MarkdownParser
 * @see MarkdownWriter
 */
public abstract class Block {

    /**
     * Erstellt einen Block-Basistyp für konkrete Markdown-Elemente.
     *
     * <p>Der öffentliche Konstruktor ermöglicht Implementierungen außerhalb
     * dieses Moduls, eigene Blocktypen abzuleiten.
     */
    public Block() {
    }
}
