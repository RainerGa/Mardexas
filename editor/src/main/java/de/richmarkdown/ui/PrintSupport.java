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
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

/**
 * Aufbau des Ausdrucks (PROJEKTPLAN 14): Seitenzahl, Schichtversatz und
 * Messung der vollen Dokumenthöhe.
 *
 * <p>JavaFX rendert mit {@code PrinterJob.printPage(node)} genau eine Seite –
 * ein langes Dokument wird nicht automatisch paginiert. Der Ausdruck wird
 * daher seitenweise geschichtet: Die Druckkopie wird auf die volle
 * Dokumenthöhe gesetzt und pro Seite um die bedruckbare Seitenhöhe
 * verschoben.
 *
 * <p>Die volle Dokumenthöhe wird über die Absatz-Zellen der Bibliothek
 * gemessen: Jeder Absatz wird per {@code scrollTo(index)} in den Viewport
 * geholt und seine gerenderte Zellhöhe aufsummiert. Das ist robust – der
 * VirtualFlow der Bibliothek fragmentiert bei sehr großen Viewports, eine
 * direkte Messung über {@code boundsInParent} wäre davon abhängig.
 * ({@code RichTextArea.getFullHeight()} ist in der gebundenen Bibliothek
 * ein offener Punkt und meldet immer 0.)
 */
final class PrintSupport {

    private PrintSupport() {
    }

    /**
     * Misst die volle Dokumenthöhe der Druckkopie (FX-Thread, Skin nötig).
     * Für ein leeres Dokument ist das Ergebnis 0.
     */
    static double measureContentHeight(RichTextArea area) {
        if (area.getTextLength() == 0) {
            return 0d;
        }
        Object lookup = area.lookup(".paragraph-list-view");
        ListView<?> listView = lookup instanceof ListView<?> lv ? lv : null;
        if (listView == null) {
            return Math.max(area.getHeight(), 0d);
        }
        double height = 0d;
        int items = listView.getItems().size();
        // Der VirtualFlow der Bibliothek erzeugt Zellen nur lazy für den
        // sichtbaren Bereich und behält einen begrenzten Zell-Vorrat. Damit
        // jeder Absatz mit seiner natürlichen Höhe gemessen wird, wird
        // nacheinander zu jedem Index gescrollt, ein Layout erzwungen und die
        // Höhe der gerade dargestellten Zelle aufsummiert.
        listView.applyCss();
        for (int i = 0; i < items; i++) {
            listView.scrollTo(i);
            listView.layout();
            ListCell<?> cell = cellAtIndex(listView, i);
            if (cell != null && cell.getIndex() >= 0) {
                height += cell.getHeight();
            }
        }
        // Scroll zurück an den Anfang, sodass spätere Aufrufer (z. B. eine
        // Konsistenz-Prüfung mit vollem Viewport) nicht von der letzten
        // Scroll-Position abhängen.
        listView.scrollTo(0);
        listView.applyCss();
        listView.layout();
        return height;
    }

    private static ListCell<?> cellAtIndex(ListView<?> listView, int index) {
        for (Node node : listView.lookupAll(".list-cell")) {
            if (node instanceof ListCell<?> cell && cell.getIndex() == index) {
                return cell;
            }
        }
        return null;
    }

    /** Seitenzahl für die gegebene Dokumenthöhe (mindestens eine Seite). */
    static int pageCount(double contentHeight, double printableHeight) {
        if (contentHeight <= 0d || printableHeight <= 0d) {
            return 1;
        }
        return (int) Math.ceil(contentHeight / printableHeight);
    }

    /** Vertikaler Versatz der Druckkopie für die gegebene Seite (0-basiert). */
    static double sliceOffset(int page, double printableHeight) {
        return -page * printableHeight;
    }
}
