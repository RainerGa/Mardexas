package de.richmarkdown.ui;

import com.gluonhq.richtextarea.RichTextArea;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

import java.util.List;

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
        @SuppressWarnings("unchecked")
        ListView<Node> listView = (ListView<Node>) area.lookup(".paragraph-list-view");
        if (listView == null) {
            return Math.max(area.getHeight(), 0d);
        }
        double height = 0d;
        int items = listView.getItems().size();
        // Die Zellen des VirtualFlow werden wiederverwendet – einmal einsammeln
        // und dann nur noch Index/Höhe lesen spart den Szenen-Scan pro Absatz
        List<ListCell<?>> cells = List.of();
        boolean cssApplied = false;
        for (int index = 0; index < items; index++) {
            listView.scrollTo(index);
            if (cssApplied) {
                listView.requestLayout();
            } else {
                listView.applyCss();
                cssApplied = true;
            }
            listView.layout();
            double cellHeight = heightOfCell(cells, index);
            if (cellHeight < 0d) {
                cells = listView.lookupAll(".list-cell").stream()
                        .filter(ListCell.class::isInstance)
                        .<ListCell<?>>map(ListCell.class::cast)
                        .toList();
                cellHeight = heightOfCell(cells, index);
            }
            if (cellHeight > 0d) {
                height += cellHeight;
            }
        }
        listView.scrollTo(0);
        return height;
    }

    /** Höhe der Zelle mit dem gegebenen Index, oder -1 wenn nicht vorhanden. */
    private static double heightOfCell(List<ListCell<?>> cells, int index) {
        for (ListCell<?> cell : cells) {
            if (cell.getIndex() == index) {
                return cell.getHeight();
            }
        }
        return -1d;
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
