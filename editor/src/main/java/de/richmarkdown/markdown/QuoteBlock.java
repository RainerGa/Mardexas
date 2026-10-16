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
 * Blockzitat, das eine geordnete Folge beliebiger Markdown-Blöcke enthalten kann.
 *
 * <p>Die rekursive Struktur ermöglicht unter anderem Zitate mit mehreren
 * Absätzen, Listen oder weiteren Zitaten. Die Kinderliste wird bei Erzeugung
 * defensiv kopiert.
 */
public class QuoteBlock extends Block {

    private final List<Block> children;

    /**
     * Erstellt ein Zitat aus den enthaltenen Blöcken.
     *
     * @param children verschachtelte Blöcke in ihrer Dokumentreihenfolge
     */
    public QuoteBlock(List<Block> children) {
        this.children = List.copyOf(children);
    }

    /**
     * Liefert die unveränderliche Folge der Zitatblöcke.
     *
     * @return enthaltene Blöcke in ihrer Dokumentreihenfolge
     */
    public List<Block> getChildren() {
        return children;
    }
}
