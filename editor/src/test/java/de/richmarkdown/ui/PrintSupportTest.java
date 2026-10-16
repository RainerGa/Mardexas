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
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD: Druck-Aufbau (PROJEKTPLAN 14) – Seitenzahl je Dokumenthöhe,
 * Schichtversatz je Seite und Messung der vollen Dokumenthöhe.
 *
 * <p>Gemessen wird über die Absatz-Zellen der Bibliothek: Der VirtualFlow
 * platziert Zellen absolut ab dem Strom-Ursprung, sodass das Maximum von
 * {@code boundsInParent.maxY} genau dem Dokumentende entspricht – sobald der
 * Viewport groß genug ist (Verdopplungsschleife).
 */
class PrintSupportTest {

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    @AfterAll
    static void tearDown() {
        // kein hide(): blockt den FX-Thread in Monocle headless
    }

    // ---------- Seitenzahl ----------

    @Test
    void pageCountCoversWholeDocument() {
        assertEquals(1, PrintSupport.pageCount(400d, 400d));
        assertEquals(2, PrintSupport.pageCount(401d, 400d));
        assertEquals(2, PrintSupport.pageCount(800d, 400d));
        assertEquals(3, PrintSupport.pageCount(1000d, 400d));
        // leeres Dokument ergibt genau eine Seite
        assertEquals(1, PrintSupport.pageCount(0d, 400d));
    }

    // ---------- Schichtung ----------

    @Test
    void sliceOffsetMovesUpByPageHeight() {
        assertEquals(0d, PrintSupport.sliceOffset(0, 400d), 0.001);
        assertEquals(-400d, PrintSupport.sliceOffset(1, 400d), 0.001);
        assertEquals(-800d, PrintSupport.sliceOffset(2, 400d), 0.001);
        assertEquals(-300d, PrintSupport.sliceOffset(1, 300d), 0.001);
    }

    @Test
    void everySliceIsFullyCoveredByPages() {
        // Für beliebige Dokumenthöhen muss die Summe der Seitenhöhen
        // mindestens die Dokumenthöhe abdecken
        for (double content : new double[] {1d, 399d, 400d, 401d, 1234d, 12345d}) {
            int pages = PrintSupport.pageCount(content, 400d);
            assertTrue(pages * 400d >= content, pages + " pages for " + content);
        }
    }

    // ---------- Höhenmessung ----------

    @Test
    void measureContentHeightCoversWholeDocument() {
        // 60 Zeilen in einem Absatz (Soft-Breaks) – deutlich mehr als ein
        // Bildschirmausschnitt; passt auf keine Seite
        String markdown = String.join("\n", Collections.nCopies(60,
                "Eine Zeile mit etwas mehr Text damit sie umbricht"));
        RichTextArea area = printCopyOf(markdown);

        double contentHeight = FxTestUtil.callAndWait(
                () -> PrintSupport.measureContentHeight(area));

        assertTrue(contentHeight > 500d,
                "expected more than a viewport, measured " + contentHeight);
        assertTrue(contentHeight < 2000d,
                "expected less than 2000px, measured " + contentHeight);

        // Nach dem Setzen auf die gemessene Höhe muss der gesamte Inhalt
        // gerendert sein – sonst würde Paginierung Inhalt unterschlagen.
        // Der VirtualFlow der Bibliothek erzeugt Zellen nur lazy für den
        // sichtbaren Bereich. Daher wird zu jedem Index gescrollt, damit alle
        // Absatz-Zellen mit natürlicher Höhe vorliegen, bevor das
        // Dokumentende (maximale Zell-Unterkante) vermessen wird.
        FxTestUtil.runAndWait(() -> {
            area.setPrefHeight(contentHeight);
            area.resize(area.getWidth(), contentHeight);
            area.applyCss();
            area.layout();
        });
        FxTestUtil.flushFxEvents();
        // Scroll-Offset auf den Anfang zurücksetzen, damit die Zellen bei
        // absoluter Position null beginnen und die maximale
        // boundsInParent.maxY das wahre Dokumentende widerspiegelt.
        FxTestUtil.runAndWait(() -> {
            ListView<?> lv = (ListView<?>) area.lookup(".paragraph-list-view");
            lv.scrollTo(0);
            lv.applyCss();
            lv.layout();
        });
        FxTestUtil.flushFxEvents();
        // Alle Zellen erzwingen: Der VirtualFlow der Bibliothek erzeugt Zellen
        // nur lazy für den sichtbaren Bereich. Daher wird zu jedem Index
        // gescrollt, damit alle Absatz-Zellen mit natürlicher Höhe vorliegen,
        // bevor das Dokumentende (maximale Zell-Unterkante) vermessen wird.
        FxTestUtil.runAndWait(() -> {
            ListView<?> lv = (ListView<?>) area.lookup(".paragraph-list-view");
            int items = lv.getItems().size();
            for (int i = 0; i < items; i++) {
                lv.scrollTo(i);
                lv.applyCss();
                lv.layout();
            }
            lv.scrollTo(0);
            lv.applyCss();
            lv.layout();
        });
        FxTestUtil.flushFxEvents();
        Double maxY = FxTestUtil.callAndWait(() -> {
            ListView<?> listView = (ListView<?>) area.lookup(".paragraph-list-view");
            double max = 0d;
            for (Node node : listView.lookupAll(".list-cell")) {
                if (node instanceof ListCell<?> cell && cell.getIndex() >= 0) {
                    max = Math.max(max, cell.getBoundsInParent().getMaxY());
                }
            }
            return max;
        });
        assertEquals(contentHeight, maxY, 2d);
    }

    @Test
    void measureContentHeight_sumsEveryParagraph() {
        // 20 echte Absätze (mit Leerzeilen) – jede Zelle wird einzeln
        // gemessen und aufsummiert
        String markdown = String.join("\n\n", Collections.nCopies(20, "Ein Absatz mit etwas Text"));
        RichTextArea area = printCopyOf(markdown);

        double contentHeight = FxTestUtil.callAndWait(
                () -> PrintSupport.measureContentHeight(area));

        // 20 Absätze + 19 Leerzeilen-Absätze = 39 Absätze à ~23px
        assertTrue(contentHeight > 850d, "measured " + contentHeight);
        assertTrue(contentHeight < 950d, "measured " + contentHeight);
    }

    @Test
    void measureContentHeight_emptyDocumentIsZero() {
        RichTextArea area = printCopyOf("");
        assertEquals(0d, FxTestUtil.callAndWait(
                () -> PrintSupport.measureContentHeight(area)), 0.001d);
    }

    /** Druckkopie eines Dokuments in einer angezeigten Szene (Skin + Layout). */
    private RichTextArea printCopyOf(String markdown) {
        MarkdownEditor editor = new MarkdownEditor();
        FxTestUtil.runAndWait(() -> {
            editor.setMarkdown(markdown);
            Stage editorStage = new Stage();
            editorStage.setScene(new Scene(new Pane(editor.getControl()), 500, 400));
            editorStage.show();
        });
        FxTestUtil.flushFxEvents();
        RichTextArea copy = FxTestUtil.callAndWait(editor::createPrintCopy);
        FxTestUtil.runAndWait(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(new Pane(copy), 500, 400));
            stage.show();
            copy.setPrefWidth(500);
        });
        // Skin existiert nach einem Puls; das Laden der Bibliothek braucht
        // noch einen (wie im Druckablauf)
        FxTestUtil.flushFxEvents();
        FxTestUtil.runAndWait(() -> editor.loadPrintCopy(copy));
        FxTestUtil.flushFxEvents();
        FxTestUtil.flushFxEvents();
        WaitForAsyncUtils.waitForFxEvents();
        return copy;
    }
}
