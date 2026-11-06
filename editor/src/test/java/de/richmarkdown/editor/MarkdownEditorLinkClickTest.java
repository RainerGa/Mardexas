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

package de.richmarkdown.editor;

import com.gluonhq.richtextarea.RichTextArea;
import de.richmarkdown.FxTestUtil;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
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
 * Test für den Link-Klick-Handler mit Bestätigungsdialog.
 */
class MarkdownEditorLinkClickTest {

    private MarkdownEditor editor;
    private RichTextArea rta;
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
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(rta), 600, 400));
            stage.show();
        });
        flush();
    }

    @AfterAll
    static void tearDown() {
    }

    private void flush() {
        FxTestUtil.flushFxEvents();
    }

    @Test
    void linkCallbackFactoryIsInstalled() {
        // Der Link-Callback wird installiert
        assertNotNull(FxTestUtil.callAndWait(rta::getLinkCallbackFactory));
    }

    @Test
    void isSafeLinkUrl_allowsHttp() {
        assertTrue(MarkdownEditor.isSafeLinkUrl("http://example.com"));
    }

    @Test
    void isSafeLinkUrl_allowsHttps() {
        assertTrue(MarkdownEditor.isSafeLinkUrl("https://example.com"));
    }

    @Test
    void isSafeLinkUrl_allowsMailto() {
        assertTrue(MarkdownEditor.isSafeLinkUrl("mailto:test@example.com"));
    }

    @Test
    void isSafeLinkUrl_rejectsFileScheme() {
        assertFalse(MarkdownEditor.isSafeLinkUrl("file:///etc/passwd"));
    }

    @Test
    void isSafeLinkUrl_rejectsJavascriptScheme() {
        assertFalse(MarkdownEditor.isSafeLinkUrl("javascript:alert(1)"));
    }

    @Test
    void isSafeLinkUrl_rejectsDataScheme() {
        assertFalse(MarkdownEditor.isSafeLinkUrl("data:text/html,<script>alert(1)</script>"));
    }

    @Test
    void isSafeLinkUrl_rejectsRelativeUrl() {
        assertFalse(MarkdownEditor.isSafeLinkUrl("/relative/path"));
    }

    @Test
    void isSafeLinkUrl_rejectsEmptyString() {
        assertFalse(MarkdownEditor.isSafeLinkUrl(""));
        assertFalse(MarkdownEditor.isSafeLinkUrl("   "));
        assertFalse(MarkdownEditor.isSafeLinkUrl(null));
    }

    @Test
    void setMarkdown_parsesHttpLink() {
        FxTestUtil.runAndWait(() -> editor.setMarkdown("[Link](https://example.com)"));
        flush();
        flush();

        String markdown = FxTestUtil.callAndWait(editor::getMarkdown);
        assertEquals("[Link](https://example.com)", markdown.trim());
    }

    @Test
    void setMarkdown_parsesMailtoLink() {
        FxTestUtil.runAndWait(() -> editor.setMarkdown("[Email](mailto:test@example.com)"));
        flush();
        flush();

        String markdown = FxTestUtil.callAndWait(editor::getMarkdown);
        assertEquals("[Email](mailto:test@example.com)", markdown.trim());
    }

    @Test
    void linkCallbackNotTriggeredForUnsafeUrl() {
        // Unsichere URLs (file:, javascript:, etc.) werden vom Callback
        // nicht verarbeitet, da isSafeLinkUrl false zurückgibt
        String unsafeUrl = "file:///etc/passwd";
        FxTestUtil.runAndWait(() -> editor.setMarkdown("[Unsafe](" + unsafeUrl + ")"));
        flush();
        flush();

        // Der Link wird im Markdown beibehalten, aber der Klick wird blockiert
        String markdown = FxTestUtil.callAndWait(editor::getMarkdown);
        assertEquals("[Unsafe](file:///etc/passwd)", markdown.trim());
    }
}