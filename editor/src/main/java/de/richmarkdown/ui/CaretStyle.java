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

package de.richmarkdown.ui;

import com.gluonhq.richtextarea.RichTextArea;
import com.gluonhq.richtextarea.model.ParagraphDecoration;
import com.gluonhq.richtextarea.model.TextDecoration;
import de.richmarkdown.markdown.MarkdownStyles;
import javafx.scene.text.FontWeight;

/**
 * Ermittelt den Absatzstil an der Caret-Position. Gemeinsame Logik für
 * FormatToolBar (Dropdown) und StatusBar (PROJEKTPLAN 5.2).
 */
final class CaretStyle {

    private CaretStyle() {
    }

    /**
     * Erkennt den Stil des Absatzes an der Caret-Position:
     * Überschriften über die kanonische Schriftgröße + bold, Zitat über
     * Einrückungs-Ebene &gt; 0 ohne graphicType, sonst Absatz.
     */
    static FormatToolBar.ParagraphStyle detect(RichTextArea control) {
        if (control.getDecorationAtCaret() instanceof TextDecoration td) {
            int level = MarkdownStyles.headingLevel(td.getFontSize());
            if (level > 0 && td.getFontWeight() == FontWeight.BOLD) {
                return FormatToolBar.ParagraphStyle.values()[level]; // H1..H6
            }
        }
        ParagraphDecoration pd = control.getDecorationAtParagraph();
        if (pd != null
                && pd.getGraphicType() == ParagraphDecoration.GraphicType.NONE
                && pd.getIndentationLevel() > 0) {
            return FormatToolBar.ParagraphStyle.QUOTE;
        }
        return FormatToolBar.ParagraphStyle.BODY;
    }
}
