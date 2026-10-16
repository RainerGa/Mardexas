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
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD: Zeilennummern-Anzeige (Gutter) links vom Editor – PROJEKTPLAN, Nachtrag
 * Zeilennummern (ergänzend zur Dokumentstruktur).
 *
 * <p>Hinweis zum Dokumentaufbau: Der Markdown-Parser behandelt einzelne
 * Zeilenumbrüche als weichen Umbruch innerhalb eines Absatzes. Mehrzeilige
 * Testdokumente werden daher mit Leerzeilen getrennt – so entstehen echte
 * Absätze und damit mehrere numerierte Zeilen.
 */
class LineNumberGutterTest {

    private MarkdownEditor editor;
    private RichTextArea rta;
    private LineNumberGutter gutter;
    private ZoomController zoomController;
    private Stage stage;

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    @BeforeEach
    void setUp() {
        // Fremde Ausnahmen abräumen: TestFX speichert Ausnahmen vom FX-Thread
        // global zwischen (installiert durch waitForFxEvents). Vorangehende
        // Testklassen (z. B. Bild-Tests mit nicht ladbaren URLs) dürfen diesen
        // Test nicht belasten.
        WaitForAsyncUtils.clearExceptions();
        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor();
            rta = editor.getControl();
            zoomController = new ZoomController(rta);
            gutter = new LineNumberGutter(rta, zoomController.zoomProperty());
            // Layout wie im EditorWindow: Gutter links neben dem Zoom-Viewport
            HBox row = new HBox(gutter, zoomController.getViewport());
            HBox.setHgrow(zoomController.getViewport(), Priority.ALWAYS);
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(row), 800, 400));
            stage.show();
        });
        flushFully();
    }

    @AfterAll
    static void tearDown() {
        // kein hide(): blockt den FX-Thread in Monocle headless
    }

    // ---------- Ein-/Ausblenden ----------

    @Test
    @DisplayName("ist initial ausgeblendet und unmanaged")
    void hiddenByDefault() {
        assertFalse(gutter.isVisible());
        assertFalse(gutter.isManaged());
        assertTrue(numberLabels().isEmpty());
    }

    @Test
    @DisplayName("zeigt nach dem Einblenden Nummern für die sichtbaren Zeilen")
    void showsNumbersWhenEnabled() {
        editor.setMarkdown("alpha\n\nbeta\n\ngamma");
        flushFully();
        showGutter();
        flushFully();

        List<String> numbers = numbers();
        assertFalse(numbers.isEmpty());
        assertEquals("1", numbers.get(0));
        assertEquals("2", numbers.get(1));
        assertEquals("3", numbers.get(2));
    }

    // ---------- Ausrichtung ----------

    @Test
    @DisplayName("jede Nummer steht auf der Höhe ihrer Editor-Zeile")
    void numbersAlignWithEditorRows() {
        editor.setMarkdown("# Überschrift\n\nAbsatz mit Text\n\n- Liste\n- Liste2\n\nLetzter Absatz");
        flushFully();
        showGutter();
        flushFully();

        List<Label> labels = numberLabels();
        assertFalse(labels.isEmpty());
        for (Label label : labels) {
            int line = Integer.parseInt(label.getText());
            ListCell<?> cell = cellForIndex(line - 1);
            assertNotNull(cell, "Editor-Zelle für Zeile " + line + " nicht gefunden");

            double labelCenter = label.localToScene(0, label.getHeight() / 2).getY();
            double cellTop = cell.localToScene(0, 0).getY();
            double cellBottom = cell.localToScene(0, cell.getHeight()).getY();
            assertTrue(labelCenter >= cellTop - 2 && labelCenter <= cellBottom + 2,
                    "Nummer " + line + " (Mitte " + labelCenter + ") liegt nicht in ihrer Zeile ["
                            + cellTop + ", " + cellBottom + "]");
        }
    }

    @Test
    @DisplayName("die Nummernfolge ist lückenlos und aufsteigend")
    void numbersAreConsecutive() {
        editor.setMarkdown(String.join("\n\n", Collections.nCopies(30, "Zeile")));
        flushFully();
        showGutter();
        flushFully();

        List<String> numbers = numbers();
        assertTrue(numbers.size() > 5,
                "bei 30 Absätzen müssen deutlich mehr als 5 Nummern sichtbar sein, waren "
                        + numbers.size());
        for (int i = 0; i < numbers.size(); i++) {
            assertEquals(String.valueOf(i + 1), numbers.get(i),
                    "die " + (i + 1) + ". sichtbare Nummer muss " + (i + 1) + " sein");
        }
    }

    // ---------- Scrollen ----------

    @Test
    @DisplayName("folgt dem Scrollen des Editors (Mausrad)")
    @SuppressWarnings("deprecation")
    void followsScrolling() {
        FxRobot robot = new FxRobot();
        editor.setMarkdown(String.join("\n\n", Collections.nCopies(40, "Zeile")));
        flushFully();
        showGutter();
        flushFully();

        List<String> before = numbers();
        assertFalse(before.isEmpty());

        // Vertikalen Scrollbalken der Editor-Liste direkt bewegen (statt über
        // robot.scroll(), das den ScrollBar-Wert in headless-Umgebungen nicht
        // zuverlässig ändert). Die Richtung wird anhand der Scrollposition
        // gewählt, damit in beiden Ausgangslagen (Ansprung oben oder unten)
        // Bewegung entsteht.
        FxTestUtil.runAndWait(() -> {
            for (Node node : rta.lookupAll(".scroll-bar")) {
                if (node instanceof ScrollBar bar
                        && bar.getOrientation() == Orientation.VERTICAL) {
                    double target = bar.getValue() < bar.getMax() / 2
                            ? bar.getMax() : bar.getMin();
                    bar.setValue(target);
                }
            }
        });
        flushFully();
        // Sicherstellen, dass die Gutter-Positionen nach dem Scroll neu berechnet werden
        gutter.requestSync();
        flushFully();

        List<String> after = numbers();
        assertNotEquals(before.get(0), after.get(0),
                "die erste sichtbare Nummer muss dem Scrollen folgen");
        int first = Integer.parseInt(after.get(0));
        assertTrue(first > 1, "erste sichtbare Nummer sollte größer als 1 sein, war " + first);
        // und die Nummern bleiben aufsteigend und lückenlos
        for (int i = 1; i < after.size(); i++) {
            assertEquals(first + i, Integer.parseInt(after.get(i)));
        }
    }

    // ---------- Dokumentänderungen ----------

    @Test
    @DisplayName("aktualisiert sich bei Dokumentwechsel")
    void updatesWhenDocumentChanges() {
        showGutter();
        editor.setMarkdown("one\n\ntwo");
        flushFully();
        assertEquals(List.of("1", "2", "3"), numbers());

        editor.setMarkdown("solo");
        flushFully();
        assertEquals(List.of("1"), numbers());
    }

    @Test
    @DisplayName("aktualisiert sich beim Tippen (neue Zeile)")
    void updatesWhenTyping() {
        showGutter();
        editor.setMarkdown("eins");
        flushFully();
        assertEquals(List.of("1"), numbers());

        FxTestUtil.runAndWait(() -> rta.getActionFactory()
                .insertText("\nzwei")
                .execute(new javafx.event.ActionEvent()));
        flushFully();
        flushFully();

        List<String> numbers = numbers();
        assertTrue(numbers.size() >= 2, "nach dem Umbruch müssen mindestens 2 Nummern stehen");
        assertEquals("1", numbers.get(0));
        assertEquals("2", numbers.get(1));
    }

    // ---------- Zoom ----------

    @Test
    @DisplayName("skaliert die Schrift mit dem Zoomfaktor")
    void scalesFontWithZoom() {
        editor.setMarkdown("alpha\n\nbeta");
        flushFully();
        showGutter();
        flushFully();

        double baseSize = numberLabels().get(0).getFont().getSize();
        assertTrue(baseSize > 0);

        FxTestUtil.runAndWait(() -> zoomController.setZoom(200));
        flushFully();

        double zoomedSize = numberLabels().get(0).getFont().getSize();
        assertEquals(baseSize * 2, zoomedSize, 0.5,
                "bei 200 % Zoom muss die Schrift doppelt so groß sein");
    }

    // ---------- Helfer ----------

    private void showGutter() {
        FxTestUtil.runAndWait(() -> {
            gutter.setVisible(true);
            gutter.setManaged(true);
        });
    }

    private void flushFully() {
        FxTestUtil.flushFxEvents();
        FxTestUtil.flushFxEvents();
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Sichtbare Nummern-Labels, aufsteigend nach Zeilenposition sortiert. */
    private List<Label> numberLabels() {
        return FxTestUtil.callAndWait(() -> {
            List<Label> labels = new ArrayList<>();
            for (Node node : gutter.lookupAll(".line-number")) {
                if (node instanceof Label label && label.isVisible()) {
                    labels.add(label);
                }
            }
            labels.sort(Comparator.comparingDouble(Label::getLayoutY));
            return labels;
        });
    }

    private List<String> numbers() {
        return numberLabels().stream()
                .map(Label::getText)
                .collect(Collectors.toList());
    }

    /** Scrollposition der Editor-Liste (0 = oben, 1 = unten). */
    private double verticalScrollPosition() {
        return FxTestUtil.callAndWait(() -> {
            for (Node node : rta.lookupAll(".scroll-bar")) {
                if (node instanceof ScrollBar bar && bar.getOrientation() == Orientation.VERTICAL) {
                    return bar.getValue();
                }
            }
            return 0d;
        });
    }

    /** Editor-Zelle mit dem gegebenen Zeilenindex (0-basiert). */
    private ListCell<?> cellForIndex(int index) {
        return FxTestUtil.callAndWait(() -> {
            ListView<?> listView = (ListView<?>) rta.lookup(".paragraph-list-view");
            assertNotNull(listView, "Paragraph-Liste nicht gefunden");
            for (Node node : listView.lookupAll(".list-cell")) {
                if (node instanceof ListCell<?> cell && cell.getIndex() == index) {
                    return cell;
                }
            }
            return null;
        });
    }
}
