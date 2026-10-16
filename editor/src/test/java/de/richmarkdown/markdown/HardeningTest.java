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

import com.gluonhq.richtextarea.RichTextArea;
import com.gluonhq.richtextarea.model.Document;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
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
 * TDD Phase 7: Härtung – Robustheit gegen Fehleingaben, Lastverhalten,
 * Paste-Verhalten (PROJEKTPLAN 7.2).
 */
class HardeningTest {

    private final MarkdownParser parser = new MarkdownParser();
    private final MarkdownWriter writer = new MarkdownWriter();

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
    }

    @AfterAll
    static void tearDown() {
        // kein hide(): blockt den FX-Thread in Monocle headless
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

    // ---------- Robustheit: Parser ----------

    @Test
    void parserHandlesUnclosedCodeFence() {
        MarkdownModel model = parser.parse("```\nunclosed code");
        assertNotNull(model);
        assertTrue(model.getBlocks().get(0) instanceof CodeBlockBlock);
    }

    @Test
    void parserHandlesUnmatchedEmphasis() {
        // Marker ohne Partner bleiben literal (einzelner Marker-Typ)
        MarkdownModel model = parser.parse("a ** b ~~ c");
        assertEquals(1, model.getBlocks().size());
        ParagraphBlock block = (ParagraphBlock) model.getBlocks().get(0);
        assertEquals("a ** b ~~ c", block.getInlines().get(0).getText());
    }

    @Test
    void parserHandlesUnclosedLink() {
        MarkdownModel model = parser.parse("[text](unclosed");
        ParagraphBlock block = (ParagraphBlock) model.getBlocks().get(0);
        assertEquals("[text](unclosed", block.getInlines().get(0).getText());
    }

    @Test
    void parserHandlesOnlyWhitespace() {
        assertTrue(parser.parse("   \n\t\n  ").getBlocks().stream()
                .allMatch(b -> b instanceof ParagraphBlock));
    }

    @Test
    void parserHandlesNullInput() {
        assertTrue(parser.parse(null).getBlocks().isEmpty());
    }

    @Test
    void parserHandlesVeryLongLine() {
        String longLine = "x".repeat(100_000);
        MarkdownModel model = parser.parse(longLine);
        assertEquals(1, model.getBlocks().size());
    }

    @Test
    void parserHandlesDeeplyNestedQuotes() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("> ");
        }
        sb.append("deep");
        MarkdownModel model = parser.parse(sb.toString());
        assertNotNull(model);
    }

    @Test
    void parserHandlesMalformedTable() {
        // Trennzeile ohne Kopfzeile davor → normaler Absatz
        MarkdownModel model = parser.parse("| --- | --- |");
        assertTrue(model.getBlocks().get(0) instanceof ParagraphBlock);
    }

    @Test
    void parserHandlesTableWithInconsistentColumns() {
        MarkdownModel model = parser.parse("| a | b |\n| --- | --- |\n| 1 |");
        assertTrue(model.getBlocks().get(0) instanceof TableBlock);
    }

    @Test
    void parserHandlesEmptyImageUrl() {
        MarkdownModel model = parser.parse("![alt]()");
        assertTrue(model.getBlocks().get(0) instanceof ImageBlock);
    }

    @Test
    void roundTripIsStableForMalformedInput() {
        String[] inputs = {
                "```\nunclosed",
                "a ** b",
                "[text](unclosed",
                "| a | b |\n| --- | --- |\n| 1 |",
                "> > > deep",
                "- \n- ",
                "#",
                "###### ",
        };
        for (String input : inputs) {
            Document first = parser.parseInto(input);
            String written = writer.write(first);
            Document second = parser.parseInto(written);
            assertEquals(written, writer.write(second),
                    "instability for input: " + input);
        }
    }

    // ---------- Lastverhalten ----------

    @Test
    void handlesLargeDocument() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            sb.append("# Heading ").append(i).append("\n\n");
            sb.append("Paragraph ").append(i).append(" with **bold** and *italic*.\n\n");
            if (i % 10 == 0) {
                sb.append("- item a\n- item b\n\n");
            }
        }
        // Der Generator endet mit zwei Newlines; der Writer normalisiert auf
        // ein abschließendes Newline
        String markdown = sb.toString().stripTrailing() + "\n";

        long start = System.currentTimeMillis();
        Document document = parser.parseInto(markdown);
        String written = writer.write(document);
        long duration = System.currentTimeMillis() - start;

        assertEquals(markdown, written);
        // Weiche Zeitgrenze: das Round-Trip eines großen Dokuments muss
        // in angemessener Zeit abgeschlossen sein (Sandbox-toleranz)
        assertTrue(duration < 60_000,
                "round trip took too long: " + duration + " ms");
    }

    @Test
    void handlesManyListItems() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append("- item ").append(i);
        }
        String markdown = sb.toString();
        assertEquals(markdown, writer.write(parser.parseInto(markdown)));
    }

    @Test
    void handlesLargeTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("| a | b | c |\n| --- | --- | --- |\n");
        for (int i = 0; i < 500; i++) {
            sb.append("| ").append(i).append(" | x | y |\n");
        }
        String markdown = sb.toString().stripTrailing();
        assertEquals(markdown, writer.write(parser.parseInto(markdown)));
    }

    // ---------- Editor: Lastverhalten ----------

    @Test
    void editorHandlesLargeDocument() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb.append("Paragraph ").append(i).append("\n\n");
        }
        String markdown = sb.toString().stripTrailing();

        FxTestUtil.runAndWait(() -> editor.setMarkdown(markdown));
        flush();

        String result = FxTestUtil.callAndWait(editor::getMarkdown);
        assertEquals(markdown, result);
    }

    // ---------- Paste ----------

    @Test
    void pasteInsertsClipboardText() {
        FxTestUtil.runAndWait(() -> {
            ClipboardContent content = new ClipboardContent();
            content.putString("pasted text");
            Clipboard.getSystemClipboard().setContent(content);
        });
        type("before ");
        FxTestUtil.runAndWait(() -> rta.getActionFactory().paste().execute(new ActionEvent()));
        flush();

        String text = FxTestUtil.callAndWait(editor::getMarkdown);
        assertTrue(text.contains("pasted text"), "pasted text expected in: " + text);
    }

    @Test
    void copyAndPasteRoundTrip() {
        type("copy me");
        FxTestUtil.runAndWait(() -> rta.getActionFactory().selectAll().execute(new ActionEvent()));
        flush();
        FxTestUtil.runAndWait(() -> rta.getActionFactory().copy().execute(new ActionEvent()));
        flush();

        // Auswahl aufheben und am Ende einfügen
        FxTestUtil.runAndWait(() -> rta.getActionFactory().selectNone().execute(new ActionEvent()));
        flush();
        FxTestUtil.runAndWait(() -> rta.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "",
                KeyCode.END, false, false, false, false)));
        FxTestUtil.runAndWait(() -> rta.getActionFactory().paste().execute(new ActionEvent()));
        flush();

        String text = FxTestUtil.callAndWait(editor::getMarkdown);
        assertEquals("copy mecopy me", text);
    }

    @Test
    void pasteOfEmptyClipboardDoesNotCorrupt() {
        FxTestUtil.runAndWait(() -> Clipboard.getSystemClipboard().clear());
        type("text");
        FxTestUtil.runAndWait(() -> rta.getActionFactory().paste().execute(new ActionEvent()));
        flush();

        assertEquals("text", FxTestUtil.callAndWait(editor::getMarkdown));
    }

    // ---------- Fehlerbehandlung Editor ----------

    @Test
    void setMarkdownWithMalformedInputDoesNotThrow() {
        String[] inputs = {"```\nunclosed", "a ** b", "[x](y", "| a |\n| --- |"};
        for (String input : inputs) {
            FxTestUtil.runAndWait(() -> editor.setMarkdown(input));
            flush();
            assertNotNull(FxTestUtil.callAndWait(editor::getMarkdown));
        }
    }

    @Test
    void getMarkdownAfterManyEditsIsConsistent() {
        for (int i = 0; i < 50; i++) {
            type("x");
            editor.toggleBold();
            flush();
            editor.toggleBold();
            flush();
        }
        String markdown = FxTestUtil.callAndWait(editor::getMarkdown);
        assertNotNull(markdown);
        assertFalse(markdown.isEmpty());
    }

    @Test
    void undoRedoAfterManyEditsRestoresState() {
        type("hello");
        editor.toggleBold();
        flush();
        for (int i = 0; i < 10; i++) {
            FxTestUtil.runAndWait(() -> rta.getActionFactory().undo().execute(new ActionEvent()));
            flush();
        }
        // Alles zurückgenommen: Dokument ist leer
        assertEquals("", FxTestUtil.callAndWait(editor::getMarkdown));
    }
}
