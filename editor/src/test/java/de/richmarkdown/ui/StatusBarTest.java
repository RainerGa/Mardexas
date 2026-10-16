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
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD Phase 1: StatusBar – Geändert-Status, Cursor-Position, Absatzstil.
 */
class StatusBarTest {

    private MarkdownEditor editor;
    private RichTextArea rta;
    private StatusBar statusBar;
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
            statusBar = new StatusBar(editor);
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(statusBar, rta), 800, 400));
            stage.show();
        });
    }

    @AfterAll
    static void tearDown() {
        // kein stage.hide(): blockt den FX-Thread in Monocle headless
    }

    private void flush() {
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

    @Test
    void statusShowsNoMarkerInitially() {
        assertEquals("", FxTestUtil.callAndWait(() -> statusBar.getStatusLabel().getText()));
    }

    @Test
    void statusShowsAsteriskAfterTyping() {
        type("x");
        flush();
        assertEquals("*", FxTestUtil.callAndWait(() -> statusBar.getStatusLabel().getText()));
    }

    @Test
    void statusClearsAsteriskAfterSave() {
        type("x");
        flush();
        FxTestUtil.runAndWait(() -> rta.getActionFactory().save().execute(new ActionEvent()));
        flush();
        assertEquals("", FxTestUtil.callAndWait(() -> statusBar.getStatusLabel().getText()));
    }

    @Test
    void positionTracksCaretAndLength() {
        type("abc");
        flush();
        assertEquals("Zeichen 3 / 3", FxTestUtil.callAndWait(() -> statusBar.getPositionLabel().getText()));
    }

    @Test
    void styleShowsBodyByDefault() {
        assertEquals("Stil: Absatz", FxTestUtil.callAndWait(() -> statusBar.getStyleLabel().getText()));
    }

    @Test
    void styleShowsHeadingAfterApply() {
        type("Title");
        FxTestUtil.runAndWait(() -> rta.getActionFactory().selectAll().execute(new ActionEvent()));
        flush();
        editor.applyHeading(1);
        flush();
        FxTestUtil.runAndWait(() -> rta.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "",
                KeyCode.HOME, false, false, false, false)));
        flush();

        assertEquals("Stil: Überschrift 1", FxTestUtil.callAndWait(() -> statusBar.getStyleLabel().getText()));
    }

    @Test
    void styleShowsQuoteAfterApply() {
        editor.applyQuote();
        flush();
        assertEquals("Stil: Zitat", FxTestUtil.callAndWait(() -> statusBar.getStyleLabel().getText()));
    }

    @Test
    void labelsAreAccessible() {
        Label status = statusBar.getStatusLabel();
        Label position = statusBar.getPositionLabel();
        Label style = statusBar.getStyleLabel();
        assertTrue(status != null && position != null && style != null);
    }
}
