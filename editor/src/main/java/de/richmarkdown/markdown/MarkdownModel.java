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
 * Unveränderliche, geordnete Zwischenrepräsentation zwischen dem Rich-Text-
 * {@code Document} und Markdown-Quelltext.
 *
 * <p>Das Modell hält Blockelemente in Dokumentreihenfolge. Es enthält keine
 * JavaFX-Steuerelemente und bildet dadurch die testbare Grenze zwischen
 * Parsing, Editor-Dokument und Serialisierung. Die übergebenen Blocklisten
 * werden beim Erzeugen kopiert; die zurückgegebene Liste kann nicht verändert
 * werden.
 *
 * @see MarkdownParser#parse(String)
 * @see MarkdownWriter#toModel(com.gluonhq.richtextarea.model.Document)
 * @see MarkdownWriter#write(MarkdownModel)
 */
public class MarkdownModel {

    private final List<Block> blocks;

    private MarkdownModel(List<Block> blocks) {
        this.blocks = List.copyOf(blocks);
    }

    /**
     * Erstellt ein Modell aus einer geordneten Folge von Blöcken.
     *
     * @param blocks Blöcke in Dokumentreihenfolge; darf leer sein
     * @return neues, unveränderliches Modell
     */
    public static MarkdownModel of(Block... blocks) {
        return new MarkdownModel(List.of(blocks));
    }

    /**
     * Erstellt ein Modell aus einer geordneten Blockliste.
     *
     * @param blocks Blöcke in Dokumentreihenfolge; darf leer sein
     * @return neues, unveränderliches Modell
     */
    public static MarkdownModel of(List<Block> blocks) {
        return new MarkdownModel(blocks);
    }

    /**
     * Liefert die unveränderliche Blockfolge des Modells.
     *
     * @return Blöcke in ihrer ursprünglichen Dokumentreihenfolge
     */
    public List<Block> getBlocks() {
        return blocks;
    }

    /**
     * Prüft, ob das Modell keine Blöcke enthält.
     *
     * @return {@code true}, wenn die Blockliste leer ist
     */
    public boolean isEmpty() {
        return blocks.isEmpty();
    }
}
