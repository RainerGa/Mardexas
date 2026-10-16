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
 * Semantische horizontale Trennlinie, die beim Schreiben als Markdown-
 * Horizontal-Rule-Marker ausgegeben wird.
 *
 * <p>Der Block trägt keine konfigurierbaren Daten; die konkrete Schreibweise
 * wird vom {@link MarkdownWriter} bestimmt.
 */
public class HorizontalRuleBlock extends Block {

    /** Erstellt eine horizontale Trennlinie ohne zusätzliche Attribute. */
    public HorizontalRuleBlock() {
    }
}
