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
import de.richmarkdown.editor.DocumentHeading;
import de.richmarkdown.editor.MarkdownEditor;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD: Dokumentstruktur-Anzeige (Word-Navigationsbereich / LibreOffice-Navigator)
 * – PROJEKTPLAN 7.1, Nachtrag.
 */
class DocumentStructureViewTest {

    private MarkdownEditor editor;
    private RichTextArea rta;
    private DocumentStructureView structureView;
    private Stage stage;

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    @BeforeEach
    void setUp() {
        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor();
            rta = editor.getControl();
            structureView = new DocumentStructureView(editor);
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(structureView, rta), 800, 400));
            stage.show();
        });
    }

    @AfterAll
    static void tearDown() {
        // kein hide(): blockt den FX-Thread in Monocle headless
    }

    /**
     * Doppelter Flush: {@code viewModel.open()} arbeitet in einem verschachtelten
     * {@code Platform.runLater} – der Ladevorgang schließt erst einen Puls später ab.
     */
    private void flushFully() {
        FxTestUtil.flushFxEvents();
        FxTestUtil.flushFxEvents();
    }

    private void type(String text) {
        FxTestUtil.runAndWait(() -> {
            for (char c : text.toCharArray()) {
                rta.fireEvent(new KeyEvent(KeyEvent.KEY_TYPED, String.valueOf(c), "",
                        KeyCode.UNDEFINED, false, false, false, false));
            }
        });
    }

    private ListView<DocumentHeading> list() {
        return structureView.getListView();
    }

    // ---------- Anzeige der Überschriften ----------

    @Test
    void showsAllHeadings() {
        editor.setMarkdown("# One\n\ntext\n\n## Two\n\n### Three");
        flushFully();

        assertEquals(3, list().getItems().size());
        assertEquals("One", list().getItems().get(0).text());
        assertEquals("Two", list().getItems().get(1).text());
        assertEquals("Three", list().getItems().get(2).text());
    }

    @Test
    void showsHeadingLevels() {
        editor.setMarkdown("# One\n\n## Two\n\n###### Six");
        flushFully();

        assertEquals(1, list().getItems().get(0).level());
        assertEquals(2, list().getItems().get(1).level());
        assertEquals(6, list().getItems().get(2).level());
    }

    @Test
    void emptyForDocumentWithoutHeadings() {
        editor.setMarkdown("just text\n\nmore text");
        flushFully();

        assertTrue(list().getItems().isEmpty());
    }

    @Test
    void emptyForEmptyDocument() {
        assertTrue(list().getItems().isEmpty());
    }

    @Test
    void refreshesWhenDocumentChanges() {
        editor.setMarkdown("# First");
        flushFully();
        assertEquals(1, list().getItems().size());

        editor.setMarkdown("## Second\n\n### Third");
        flushFully();
        assertEquals(2, list().getItems().size());
        assertEquals("Second", list().getItems().get(0).text());
    }

    @Test
    void refreshesWhenHeadingIsTyped() {
        type("Title");
        editor.applyHeading(1);
        flushFully();

        assertEquals(1, list().getItems().size());
        assertEquals("Title", list().getItems().get(0).text());
    }

    @Test
    void refreshesWhenHeadingIsRemoved() {
        editor.setMarkdown("# One\n\n## Two");
        flushFully();
        assertEquals(2, list().getItems().size());

        editor.setMarkdown("# One");
        flushFully();
        assertEquals(1, list().getItems().size());
    }

    // ---------- Navigation ----------

    @Test
    void clickingEntryNavigatesToHeading() {
        editor.setMarkdown("# One\n\ntext\n\n## Two\n\nmore");
        flushFully();

        FxTestUtil.runAndWait(() -> list().getSelectionModel().select(1));
        flushFully();

        int caret = FxTestUtil.callAndWait(rta::getCaretPosition);
        assertEquals(list().getItems().get(1).position(), caret);
    }

    @Test
    void clickingFirstEntryNavigatesToStart() {
        editor.setMarkdown("# One\n\ntext\n\n## Two");
        flushFully();

        // Zunächst zu einem anderen Eintrag navigieren, dann den ersten anklicken
        FxTestUtil.runAndWait(() -> list().getSelectionModel().select(1));
        flushFully();
        FxTestUtil.runAndWait(() -> list().getSelectionModel().select(0));
        flushFully();

        assertEquals(0, FxTestUtil.callAndWait(rta::getCaretPosition));
    }

    @Test
    void selectionFollowsCaret() {
        editor.setMarkdown("# One\n\ntext\n\n## Two\n\nmore");
        flushFully();

        // Caret in den zweiten Absatz (Überschrift "Two") setzen
        FxTestUtil.runAndWait(() -> editor.goToPosition(list().getItems().get(1).position()));
        flushFully();

        assertEquals(1, FxTestUtil.callAndWait(() -> list().getSelectionModel().getSelectedIndex()));
    }

    @Test
    void navigationDoesNotLoop() {
        editor.setMarkdown("# One\n\n## Two\n\n### Three");
        flushFully();

        // Mehrfache Navigation muss stabil bleiben (keine Rekursion)
        for (int index = 0; index < 3; index++) {
            final int selected = index;
            FxTestUtil.runAndWait(() -> list().getSelectionModel().select(selected));
            flushFully();
        }
        assertEquals(2, FxTestUtil.callAndWait(() -> list().getSelectionModel().getSelectedIndex()));
        assertEquals(list().getItems().get(2).position(),
                FxTestUtil.callAndWait(rta::getCaretPosition));
    }

    // ---------- Kopfzeile ----------

    @Test
    void hasHeaderLabelWithTitle() {
        assertNotNull(structureView.getHeaderLabel());
        assertEquals("Dokumentenstruktur",
                FxTestUtil.callAndWait(() -> structureView.getHeaderLabel().getText()));
    }

    @Test
    void headerIsPlacedAboveTheList() {
        boolean above = FxTestUtil.callAndWait(() -> {
            var children = structureView.getChildren();
            int headerIndex = children.indexOf(structureView.getHeaderLabel());
            int listIndex = children.indexOf(structureView.getListView());
            return headerIndex >= 0 && listIndex >= 0 && headerIndex < listIndex;
        });
        assertTrue(above, "header must be placed above the heading list");
    }

    // ---------- Darstellung ----------

    @Test
    void listUsesSingleSelection() {
        assertEquals(SelectionMode.SINGLE, list().getSelectionModel().getSelectionMode());
    }

    @Test
    void cellsAreIndentedByLevel() {
        // Einrückung wächst mit der Überschriften-Ebene
        assertTrue(DocumentStructureView.HeadingCell.indentFor(3)
                > DocumentStructureView.HeadingCell.indentFor(1));
        assertTrue(DocumentStructureView.HeadingCell.indentFor(6)
                > DocumentStructureView.HeadingCell.indentFor(2));
        assertEquals(4, DocumentStructureView.HeadingCell.indentFor(1));
    }

    @Test
    void viewIsInitiallyEmptyAndVisible() {
        assertNotNull(structureView.getListView());
        assertFalse(structureView.isVisible() == false);
    }
}
