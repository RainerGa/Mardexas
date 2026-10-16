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

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import javafx.scene.control.MenuBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD Phase 5: Datei-Operationen und Dirty-Check im EditorWindow
 * (PROJEKTPLAN 5.2, 7.1).
 */
class EditorWindowFileTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    @AfterAll
    static void tearDown() {
        // kein hide(): blockt den FX-Thread in Monocle headless
    }

    private EditorWindow newWindow() {
        EditorWindow window = FxTestUtil.callAndWait(() -> {
            EditorWindow created = new EditorWindow();
            // Anzeige ist nötig, damit der Skin (und damit die Tastaturverarbeitung)
            // aktiviert wird; hide() blockiert in Monocle headless, daher bleibt
            // das Fenster bis zum Ende der Test-JVM bestehen
            created.show();
            return created;
        });
        // Standard: Änderungen verwerfen bestätigen (injizierbar, damit kein
        // modaler Dialog den FX-Thread blockiert)
        window.setConfirmationHandler((title, message) -> true);
        return window;
    }

    private void type(EditorWindow window, String text) {
        FxTestUtil.runAndWait(() -> {
            var rta = window.getEditorControl();
            for (char c : text.toCharArray()) {
                rta.fireEvent(new KeyEvent(KeyEvent.KEY_TYPED, String.valueOf(c), "",
                        KeyCode.UNDEFINED, false, false, false, false));
            }
        });
    }

    private void pressEnter(EditorWindow window) {
        FxTestUtil.runAndWait(() -> window.getEditorControl().fireEvent(
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                        false, false, false, false)));
    }

    private void flush() {
        FxTestUtil.flushFxEvents();
    }

    // ---------- Menüstruktur ----------

    @Test
    void fileMenuExistsWithExpectedItems() {
        EditorWindow window = newWindow();
        var menuBar = FxTestUtil.callAndWait(() -> {
            var root = (javafx.scene.layout.BorderPane) window.getScene().getRoot();
            var top = (VBox) root.getTop();
            return (MenuBar) top.getChildren().get(0);
        });
        assertEquals("Datei", menuBar.getMenus().get(0).getText());

        var labels = menuBar.getMenus().get(0).getItems().stream()
                .filter(item -> !(item instanceof javafx.scene.control.SeparatorMenuItem))
                .map(item -> item.getText())
                .toList();
        assertTrue(labels.contains("Neu"));
        assertTrue(labels.contains("Öffnen..."));
        assertTrue(labels.contains("Speichern"));
        assertTrue(labels.contains("Speichern unter..."));
        assertTrue(labels.contains("Beenden"));
    }

    // ---------- Öffnen ----------

    @Test
    void openFile_loadsContentAndSetsCurrentFile() throws IOException {
        File file = tempDir.resolve("open.md").toFile();
        Files.writeString(file.toPath(), "# Title\n\ntext", java.nio.charset.StandardCharsets.UTF_8);

        EditorWindow window = newWindow();
        FxTestUtil.runAndWait(() -> window.openFile(file));
        flush();

        assertEquals("# Title\n\ntext", FxTestUtil.callAndWait(window.getEditor()::getMarkdown));
        assertEquals(file, window.getCurrentFile());
    }

    @Test
    void openFile_preservesSingleLineBreaksInEditor() throws IOException {
        File file = tempDir.resolve("lines.md").toFile();
        Files.writeString(file.toPath(), "asdf\nasdf\nasdf\nasdf",
                java.nio.charset.StandardCharsets.UTF_8);

        EditorWindow window = newWindow();
        FxTestUtil.runAndWait(() -> window.openFile(file));
        flush();

        assertEquals("asdf\nasdf\nasdf\nasdf",
                FxTestUtil.callAndWait(() -> window.getEditorControl().getDocument().getText()));
        assertEquals("asdf\nasdf\nasdf\nasdf",
                FxTestUtil.callAndWait(window.getEditor()::getMarkdown));
    }

    @Test
    void openFile_clearsModifiedFlag() throws IOException {
        File file = tempDir.resolve("clean.md").toFile();
        Files.writeString(file.toPath(), "content", java.nio.charset.StandardCharsets.UTF_8);

        EditorWindow window = newWindow();
        type(window, "dirty");
        flush();
        assertTrue(FxTestUtil.callAndWait(window.getEditor()::isModified));

        FxTestUtil.runAndWait(() -> window.openFile(file));
        flush();

        assertFalse(FxTestUtil.callAndWait(window.getEditor()::isModified));
    }

    @Test
    void openFile_updatesTitle() throws IOException {
        File file = tempDir.resolve("titled.md").toFile();
        Files.writeString(file.toPath(), "x", java.nio.charset.StandardCharsets.UTF_8);

        EditorWindow window = newWindow();
        FxTestUtil.runAndWait(() -> window.openFile(file));
        flush();

        assertTrue(FxTestUtil.callAndWait(window::getTitle).contains("titled.md"));
    }

    // ---------- Speichern ----------

    @Test
    void saveToFile_writesMarkdown() throws IOException {
        File file = tempDir.resolve("save.md").toFile();

        EditorWindow window = newWindow();
        type(window, "hello");
        flush();
        FxTestUtil.runAndWait(() -> window.saveToFile(file));
        flush();

        assertEquals("hello", Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(file, window.getCurrentFile());
    }

    @Test
    void saveToFile_preservesSingleLineBreaksBetweenTypedParagraphs() throws IOException {
        File file = tempDir.resolve("lines.md").toFile();

        EditorWindow window = newWindow();
        type(window, "asdf");
        pressEnter(window);
        type(window, "asdf");
        pressEnter(window);
        type(window, "asdf");
        pressEnter(window);
        type(window, "asdf");
        flush();

        FxTestUtil.runAndWait(() -> window.saveToFile(file));
        flush();

        assertEquals("asdf\nasdf\nasdf\nasdf",
                Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void saveToFile_clearsModifiedFlag() throws IOException {
        File file = tempDir.resolve("saved.md").toFile();

        EditorWindow window = newWindow();
        type(window, "hello");
        flush();
        assertTrue(FxTestUtil.callAndWait(window.getEditor()::isModified));

        FxTestUtil.runAndWait(() -> window.saveToFile(file));
        flush();

        assertFalse(FxTestUtil.callAndWait(window.getEditor()::isModified));
    }

    @Test
    void save_usesCurrentFile() throws IOException {
        File file = tempDir.resolve("current.md").toFile();
        Files.writeString(file.toPath(), "original", java.nio.charset.StandardCharsets.UTF_8);

        EditorWindow window = newWindow();
        FxTestUtil.runAndWait(() -> window.openFile(file));
        flush();
        type(window, " changed");
        flush();
        FxTestUtil.runAndWait(window::save);
        flush();

        assertEquals("original changed",
                Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void save_roundTripsFormattedContent() throws IOException {
        File file = tempDir.resolve("formatted.md").toFile();

        EditorWindow window = newWindow();
        FxTestUtil.runAndWait(() -> window.getEditor().setMarkdown("# Title\n\n- a\n- b"));
        flush();
        FxTestUtil.runAndWait(() -> window.saveToFile(file));
        flush();

        String content = Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("# Title\n\n- a\n- b", content);
    }

    // ---------- Dirty-Check ----------

    @Test
    void newDocument_clearsContentWhenConfirmed() {
        EditorWindow window = newWindow();
        window.setConfirmationHandler((title, message) -> true);
        type(window, "content");
        flush();

        FxTestUtil.runAndWait(window::newDocument);
        flush();

        assertEquals("", FxTestUtil.callAndWait(window.getEditor()::getMarkdown));
        assertNull(window.getCurrentFile());
    }

    @Test
    void newDocument_keepsContentWhenNotConfirmed() {
        EditorWindow window = newWindow();
        window.setConfirmationHandler((title, message) -> false);
        type(window, "content");
        flush();

        FxTestUtil.runAndWait(window::newDocument);
        flush();

        assertEquals("content", FxTestUtil.callAndWait(window.getEditor()::getMarkdown));
    }

    @Test
    void newDocument_doesNotAskWhenNotModified() {
        EditorWindow window = newWindow();
        boolean[] asked = {false};
        window.setConfirmationHandler((title, message) -> {
            asked[0] = true;
            return true;
        });

        FxTestUtil.runAndWait(window::newDocument);
        flush();

        assertFalse(asked[0], "no confirmation expected for unmodified document");
    }

    @Test
    void openFile_keepsContentWhenNotConfirmed() throws IOException {
        File file = tempDir.resolve("keep.md").toFile();
        Files.writeString(file.toPath(), "from file", java.nio.charset.StandardCharsets.UTF_8);

        EditorWindow window = newWindow();
        window.setConfirmationHandler((title, message) -> false);
        type(window, "unsaved");
        flush();

        FxTestUtil.runAndWait(() -> window.openFile(file));
        flush();

        assertEquals("unsaved", FxTestUtil.callAndWait(window.getEditor()::getMarkdown));
    }

    @Test
    void openFile_loadsWhenConfirmed() throws IOException {
        File file = tempDir.resolve("load.md").toFile();
        Files.writeString(file.toPath(), "from file", java.nio.charset.StandardCharsets.UTF_8);

        EditorWindow window = newWindow();
        window.setConfirmationHandler((title, message) -> true);
        type(window, "unsaved");
        flush();

        FxTestUtil.runAndWait(() -> window.openFile(file));
        flush();

        assertEquals("from file", FxTestUtil.callAndWait(window.getEditor()::getMarkdown));
    }

    // ---------- Fehlerbehandlung ----------

    @Test
    void openNonExistentFile_reportsError() {
        EditorWindow window = newWindow();
        File missing = tempDir.resolve("missing.md").toFile();

        // Darf nicht werfen; Fehler wird über den Error-Handler gemeldet
        FxTestUtil.runAndWait(() -> window.openFile(missing));
        flush();

        assertNotNull(window.getLastError());
        assertTrue(window.getLastError().contains("missing.md"));
    }

    @Test
    void fileHandlerIsAccessible() {
        EditorWindow window = newWindow();
        assertNotNull(window.getFileHandler());
    }

    @Test
    void editorIsAccessible() {
        EditorWindow window = newWindow();
        MarkdownEditor editor = window.getEditor();
        assertNotNull(editor);
        assertNotNull(editor.getControl());
    }
}
