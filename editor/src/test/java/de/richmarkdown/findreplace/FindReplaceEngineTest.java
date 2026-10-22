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

package de.richmarkdown.findreplace;

import com.gluonhq.richtextarea.RichTextArea;
import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.Selection;
import com.gluonhq.richtextarea.model.DecorationModel;
import com.gluonhq.richtextarea.model.Document;
import com.gluonhq.richtextarea.model.TextDecoration;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import javafx.event.ActionEvent;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Path;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suchen & Ersetzen nach LO Writer (AddFeatures #11): Textsuche, Optionen,
 * Wrap-Rueckmeldung, Ersetzen und Alle-ersetzen als ein Undo-Schritt.
 */
class FindReplaceEngineTest {
    private MarkdownEditor editor;
    private RichTextArea rta;
    private Stage stage;
    private FindReplaceEngine engine;

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
        FxTestUtil.flushFxEvents();
    }

    private void setText(String text) {
        FxTestUtil.runAndWait(() -> editor.setMarkdown(text));
        FxTestUtil.flushFxEvents();
        FxTestUtil.flushFxEvents();
        engine = FxTestUtil.callAndWait(() -> new FindReplaceEngine(rta, ((RichTextAreaSkin) rta.getSkin()).getViewModel()));
    }

    private String text() {
        FxTestUtil.runAndWait(() -> rta.getActionFactory().save().execute(new ActionEvent()));
        FxTestUtil.flushFxEvents();
        return FxTestUtil.callAndWait(() -> rta.getDocument().getText());
    }

    private void flush() {
        FxTestUtil.flushFxEvents();
    }

    private FindReplaceEngine.FindOptions opts() {
        return new FindReplaceEngine.FindOptions();
    }

    @Test
    void findNext_findsFromCaret() {
        setText("eins zwei eins");
        FindReplaceEngine.SearchResult r = FxTestUtil.callAndWait(() -> engine.findNext("eins", opts(), 0));
        assertNotNull(r);
        assertEquals(FindReplaceEngine.Wrap.FOUND, r.getWrap());
        assertEquals(0, r.getMatch().getStart());
        assertEquals(4, r.getMatch().getEnd());
    }

    @Test
    void findNext_reportsWrap() {
        setText("eins zwei eins");
        FindReplaceEngine.SearchResult r = FxTestUtil.callAndWait(() -> engine.findNext("eins", opts(), 5));
        assertNotNull(r);
        assertEquals(FindReplaceEngine.Wrap.FOUND, r.getWrap());
        assertEquals(10, r.getMatch().getStart());
        FindReplaceEngine.SearchResult w = FxTestUtil.callAndWait(() -> engine.findNext("eins", opts(), 13));
        assertNotNull(w);
        assertEquals(FindReplaceEngine.Wrap.WRAPPED, w.getWrap());
        assertEquals(0, w.getMatch().getStart());
    }

    @Test
    void findNext_noMatchReturnsNull() {
        setText("hallo welt");
        assertNull(FxTestUtil.callAndWait(() -> engine.findNext("fehlt", opts(), 0)));
    }

    @Test
    void findNext_matchCaseOption() {
        setText("Hallo hallo");
        FindReplaceEngine.FindOptions o = opts();
        o.setMatchCase(true);
        FindReplaceEngine.SearchResult r = FxTestUtil.callAndWait(() -> engine.findNext("hallo", o, 0));
        assertNotNull(r);
        assertEquals(6, r.getMatch().getStart());
    }

    @Test
    void findNext_wholeWordsOption() {
        setText("heute heute");
        FindReplaceEngine.FindOptions o = opts();
        o.setWholeWords(true);
        assertNull(FxTestUtil.callAndWait(() -> engine.findNext("heu", o, 0)));
        assertNotNull(FxTestUtil.callAndWait(() -> engine.findNext("heute", o, 0)));
    }

    @Test
    void findNext_regexOption() {
        setText("abc 123 def");
        FindReplaceEngine.FindOptions o = opts();
        o.setRegex(true);
        FindReplaceEngine.SearchResult r = FxTestUtil.callAndWait(() -> engine.findNext("[0-9]+", o, 0));
        assertNotNull(r);
        assertEquals(4, r.getMatch().getStart());
        assertEquals(7, r.getMatch().getEnd());
    }

    @Test
    void findNext_wildcardsOption() {
        setText("hallo hallo");
        FindReplaceEngine.FindOptions o = opts();
        o.setWildcards(true);
        FindReplaceEngine.SearchResult r = FxTestUtil.callAndWait(() -> engine.findNext("ha?lo", o, 0));
        assertNotNull(r);
        assertEquals(0, r.getMatch().getStart());
    }

    @Test
    void findNext_backwardsFindsPrevious() {
        setText("eins zwei eins");
        FindReplaceEngine.FindOptions o = opts();
        o.setBackwards(true);
        FindReplaceEngine.SearchResult r = FxTestUtil.callAndWait(() -> engine.findNext("eins", o, 10));
        assertNotNull(r);
        assertEquals(0, r.getMatch().getStart());
    }

    @Test
    void findAll_listsAllMatches() {
        setText("eins zwei eins drei eins");
        List<FindReplaceEngine.Match> all = FxTestUtil.callAndWait(() -> engine.findAll("eins", opts()));
        assertEquals(3, all.size());
        assertEquals(0, all.get(0).getStart());
    }

    @Test
    void searchAfterImageKeepsEditorSelectionOffsetsInSync() {
        setText("""
                # Heading One

                # Heading Two

                # Heading Three

                # Heading Four

                # Heading Five

                # Heading Six

                ![logo](https://example.com/logo.png)

                | Feature | Supported | Notes |
                |---------|-----------|-------|
                | Bold | Yes | Via toolbar or `**text**` |
                | Italic | Yes | Via toolbar or `*text*` |
                | Underline | Yes | Via toolbar |
                | Strikethrough | Yes | Via toolbar or `~~text~~` |
                | Inline Code | Yes | Via toolbar or `` `code` `` |
                | Headings | Yes | H1 through H6 |
                | Blockquote | Yes | Via `>` prefix |
                | Bulleted List | Yes | Via `-` or `*` |
                | Numbered List | Yes | Via `1.` |
                | Code Block | Yes | Via fenced blocks |
                | Horizontal Rule | Yes | Via `---` |
                | Links | Yes | Via `[text](url)` |
                | Images | Yes | Via `![alt](url)` |
                | Tables | Yes | Via pipe syntax |
                """);

        List<FindReplaceEngine.Match> matches = FxTestUtil.callAndWait(() -> engine.findAll("Heading", opts()));
        assertEquals(7, matches.size());

        FxTestUtil.runAndWait(() -> engine.selectMatch(matches.get(6)));
        flush();
        Selection selection = FxTestUtil.callAndWait(engine::getSelection);
        assertNotNull(selection);
        assertEquals("Heading".length(), selection.getLength());
        String selectedText = FxTestUtil.callAndWait(() ->
                ((RichTextAreaSkin) rta.getSkin()).getViewModel().getTextBuffer()
                        .getText(selection.getStart(), selection.getEnd()));
        assertEquals("Heading", selectedText);

        FxTestUtil.runAndWait(() -> engine.selectMatch(matches.get(0)));
        flush();
        for (int i = 1; i < matches.size(); i++) {
            FindReplaceEngine.SearchResult next = FxTestUtil.callAndWait(() -> engine.findNext("Heading", opts()));
            assertNotNull(next);
            assertEquals(matches.get(i).getStart(), next.getMatch().getStart());
            FxTestUtil.runAndWait(() -> engine.selectMatch(next.getMatch()));
        }
        flush();
        FindReplaceEngine.SearchResult wrapped = FxTestUtil.callAndWait(() -> engine.findNext("Heading", opts()));
        assertNotNull(wrapped);
        assertEquals(FindReplaceEngine.Wrap.WRAPPED, wrapped.getWrap());
        assertEquals(matches.get(0).getStart(), wrapped.getMatch().getStart());
    }

    @Test
    void findNextAfterInsertedImageMapsExportOffsetsToEditorOffsets() {
        setText("Heading\nHeading");
        FxTestUtil.runAndWait(() -> {
            ((RichTextAreaSkin) rta.getSkin()).getViewModel().setCaretPosition(7);
            editor.insertImage("https://example.com/logo.png");
        });
        flush();
        engine = FxTestUtil.callAndWait(() ->
                new FindReplaceEngine(rta, ((RichTextAreaSkin) rta.getSkin()).getViewModel()));

        List<FindReplaceEngine.Match> matches = FxTestUtil.callAndWait(() -> engine.findAll("Heading", opts()));
        assertEquals(2, matches.size());
        FxTestUtil.runAndWait(() -> engine.selectMatch(matches.get(1)));
        flush();
        Selection selection = FxTestUtil.callAndWait(engine::getSelection);
        assertNotNull(selection);
        String selectedText = FxTestUtil.callAndWait(() ->
                ((RichTextAreaSkin) rta.getSkin()).getViewModel().getTextBuffer()
                        .getText(selection.getStart(), selection.getEnd()));
        assertEquals("Heading", selectedText);

        FxTestUtil.runAndWait(() -> engine.selectMatch(matches.get(0)));
        FindReplaceEngine.SearchResult next = FxTestUtil.callAndWait(() -> engine.findNext("Heading", opts()));
        assertNotNull(next);
        assertEquals(matches.get(1).getStart(), next.getMatch().getStart());
        FxTestUtil.runAndWait(() -> engine.selectMatch(next.getMatch()));
        FindReplaceEngine.SearchResult wrapped = FxTestUtil.callAndWait(() -> engine.findNext("Heading", opts()));
        assertNotNull(wrapped);
        assertEquals(FindReplaceEngine.Wrap.WRAPPED, wrapped.getWrap());
        assertEquals(matches.get(0).getStart(), wrapped.getMatch().getStart());
    }

    @Test
    void selectingTableMatchHighlightsOnlyItsCell() {
        setText("""
                ```markdown
                This is a code block.
                ```

                ![logo](https://example.com/logo.png)
                ![screenshot](https://example.com/screenshot.png)

                | Feature | Supported |
                |---------|-----------|
                | Bold | Yes |
                | Italic | Yes |
                | Headings | Yes |
                | Tables | Yes |
                """);

        List<FindReplaceEngine.Match> matches = FxTestUtil.callAndWait(() -> engine.findAll("Heading", opts()));
        assertEquals(1, matches.size());

        FxTestUtil.runAndWait(() -> engine.selectMatch(matches.get(0), false));
        FxTestUtil.flushFxEvents();
        FxTestUtil.runAndWait(() -> {
            rta.applyCss();
            rta.layout();
            List<Node> selectedLayers = rta.lookupAll(".table .layer").stream()
                    .filter(layer -> {
                        Path selection = (Path) layer.lookup(".selection");
                        return selection != null && !selection.getElements().isEmpty();
                    })
                    .toList();
            assertEquals(1, selectedLayers.size());
            Node selectedLayer = selectedLayers.get(0);
            TextFlow textFlow = (TextFlow) selectedLayer.lookup(".text-flow");
            String cellText = textFlow.getChildren().stream()
                    .filter(Text.class::isInstance)
                    .map(Text.class::cast)
                    .map(Text::getText)
                    .collect(Collectors.joining());
            assertTrue(cellText.startsWith("Headings"));

            Path expectedSelection = new Path(textFlow.rangeShape(0, "Heading".length()));
            Bounds expectedBounds = expectedSelection.getBoundsInLocal();
            Bounds actualBounds = ((Path) selectedLayer.lookup(".selection")).getBoundsInLocal();
            assertEquals(expectedBounds.getMinX(), actualBounds.getMinX(), 0.01);
            assertEquals(expectedBounds.getMinY(), actualBounds.getMinY(), 0.01);
            assertEquals(expectedBounds.getWidth(), actualBounds.getWidth(), 0.01);
            assertEquals(expectedBounds.getHeight(), actualBounds.getHeight(), 0.01);
        });

        long selectedTableLayers = FxTestUtil.callAndWait(() -> rta.lookupAll(".table .selection").stream()
                .filter(Path.class::isInstance)
                .map(Path.class::cast)
                .filter(path -> !path.getElements().isEmpty())
                .count());
        assertEquals(1, selectedTableLayers);
    }

    @Test
    void findAll_selectionOnlyStaysInside() {
        setText("eins zwei eins");
        FxTestUtil.runAndWait(() -> {
            var vm = ((RichTextAreaSkin) rta.getSkin()).getViewModel();
            vm.setCaretPosition(0);
            vm.setSelection(new Selection(0, 9));
        });
        FindReplaceEngine.FindOptions o = opts();
        o.setSelectionOnly(true);
        List<FindReplaceEngine.Match> all = FxTestUtil.callAndWait(() -> engine.findAll("eins", o));
        assertEquals(1, all.size());
        assertEquals(0, all.get(0).getStart());
    }

    @Test
    void invalidRegex_returnsNoMatch() {
        setText("hallo");
        FindReplaceEngine.FindOptions o = opts();
        o.setRegex(true);
        assertNull(FxTestUtil.callAndWait(() -> engine.compilePattern("(", o)));
        assertNull(FxTestUtil.callAndWait(() -> engine.findNext("(", o, 0)));
        assertTrue(FxTestUtil.callAndWait(() -> engine.findAll("(", o)).isEmpty());
    }

    @Test
    void replaceSelection_replacesMarkedMatch() {
        setText("eins zwei eins");
        FxTestUtil.callAndWait(() -> {
            FindReplaceEngine.SearchResult r = engine.findNext("zwei", opts(), 0);
            engine.selectMatch(r.getMatch());
            return null;
        });
        FxTestUtil.callAndWait(() -> engine.replaceSelection("zwei", "2", opts()));
        flush();
        assertEquals("eins 2 eins", text());
    }

    @Test
    @Disabled("format preservation fix pending")
    void replaceAll_replacesEverything() {
        setText("eins zwei eins");
        int count = FxTestUtil.callAndWait(() -> engine.replaceAll("eins", "1", opts()));
        flush();
        assertEquals(2, count);
        assertEquals("1 zwei 1", text());
    }

    @Test
    @Disabled("format preservation fix pending")
    void replaceAll_regexGroups() {
        setText("a1 b2");
        FindReplaceEngine.FindOptions o = opts();
        o.setRegex(true);
        int count = FxTestUtil.callAndWait(() -> engine.replaceAll("([a-z])([0-9])", "$2$1", o));
        flush();
        assertEquals(2, count);
        assertEquals("1a 2b", text());
    }

    @Test
    void wholeWords_filtersEmbeddedOccurrences() {
        setText("eins zwei einseins meins eins");
        FindReplaceEngine.FindOptions o = opts();
        o.setWholeWords(true);
        List<FindReplaceEngine.Match> all = FxTestUtil.callAndWait(() -> engine.findAll("eins", o));
        String full = FxTestUtil.callAndWait(engine::getText);
        assertEquals(2, all.size());
        for (FindReplaceEngine.Match m : all) {
            assertEquals("eins", full.substring(m.getStart(), m.getEnd()));
        }
        assertEquals(0, all.get(0).getStart());
        assertTrue(all.get(1).getStart() > all.get(0).getEnd());
    }

    @Test
    void wholeWords_worksWithUmlauts() {
        setText("Äpfel Apfel Äpfelchen");
        FindReplaceEngine.FindOptions o = opts();
        o.setWholeWords(true);
        List<FindReplaceEngine.Match> all = FxTestUtil.callAndWait(() -> engine.findAll("Apfel", o));
        assertEquals(1, all.size());
        assertEquals(6, all.get(0).getStart());
        assertEquals(11, all.get(0).getEnd());
    }

    @Test
    @Disabled("format preservation fix pending")
    void wholeWords_wildcardReplacement() {
        setText("hallo Welt hallo Leute");
        FindReplaceEngine.FindOptions o = opts();
        o.setWildcards(true);
        o.setWholeWords(true);
        int count = FxTestUtil.callAndWait(() -> engine.replaceAll("hal?o", "hi", o));
        flush();
        assertEquals(2, count);
        assertEquals("hi Welt hi Leute", text());
    }

    @Test
    void replaceSelection_reportsReplacedWhenNoFurtherMatch() {
        setText("einmalig");
        FxTestUtil.callAndWait(() -> {
            FindReplaceEngine.SearchResult r = engine.findNext("einmalig", opts(), 0);
            engine.selectMatch(r.getMatch());
            return null;
        });
        FindReplaceEngine.SearchResult r = FxTestUtil.callAndWait(() -> engine.replaceSelection("einmalig", "X", opts()));
        flush();
        assertEquals("X", text());
        assertNotNull(r);
        assertEquals(FindReplaceEngine.Wrap.NOT_FOUND, r.getWrap());
    }

    @Test
    void replaceSelection_withoutSelectionJumpsToNextMatch() {
        setText("eins zwei eins");
        FxTestUtil.callAndWait(() -> {
            var vm = ((RichTextAreaSkin) rta.getSkin()).getViewModel();
            vm.setCaretPosition(0);
            vm.clearSelection();
            return null;
        });
        FindReplaceEngine.SearchResult r = FxTestUtil.callAndWait(() -> engine.replaceSelection("eins", "1", opts()));
        flush();
        assertEquals("eins zwei eins", text());
        assertNotNull(r);
        assertEquals(FindReplaceEngine.Wrap.FOUND, r.getWrap());
    }

    @Test
    void findNext_atDocumentEnd_stillWrapsToStart() {
        setText("eins zwei eins");
        FindReplaceEngine.FindOptions o = opts();
        int len = FxTestUtil.callAndWait(engine::getText).length();
        FindReplaceEngine.SearchResult r = FxTestUtil.callAndWait(() -> engine.findNext("eins", o, len));
        assertNotNull(r);
        assertEquals(FindReplaceEngine.Wrap.WRAPPED, r.getWrap());
        assertEquals(0, r.getMatch().getStart());
    }

    @Test
    void findNext_backwardsAtDocumentStart_stillWrapsToEnd() {
        setText("eins zwei eins");
        FindReplaceEngine.FindOptions o = opts();
        o.setBackwards(true);
        FindReplaceEngine.SearchResult r = FxTestUtil.callAndWait(() -> engine.findNext("eins", o, 0));
        assertNotNull(r);
        assertEquals(FindReplaceEngine.Wrap.WRAPPED, r.getWrap());
        String full = FxTestUtil.callAndWait(engine::getText);
        assertEquals("eins", full.substring(r.getMatch().getStart(), r.getMatch().getEnd()));
        assertEquals(full.lastIndexOf("eins"), r.getMatch().getStart());
    }

    @Test
    void replaceAll_emptyMatchesDoNotCorruptText() {
        setText("ab");
        FindReplaceEngine.FindOptions o = opts();
        o.setRegex(true);
        int count = FxTestUtil.callAndWait(() -> engine.replaceAll("x*", "-", o));
        flush();
        assertEquals(0, count);
        assertEquals("ab", text());
    }

    @Test
    void replaceAll_scopeSubstringMathIsExact() {
        setText("xx eins xx");
        int count = FxTestUtil.callAndWait(() -> engine.replaceAll("eins", "1", opts()));
        flush();
        assertEquals(1, count);
        assertEquals("xx 1 xx", text());
    }

    @Test
    @Disabled("format preservation fix pending")
    void replaceSelection_preservesBoldDecoration() {
        setText("fett und normal");
        FxTestUtil.runAndWait(() -> {
            var vm = ((RichTextAreaSkin) rta.getSkin()).getViewModel();
            vm.setSelection(new Selection(0, 4));
            rta.getActionFactory().decorate(
                    TextDecoration.builder().presets().fontWeight(FontWeight.BOLD).build())
                    .execute(new ActionEvent());
        });
        flush();
        assertEquals(FontWeight.BOLD, textDecorationAt(0).getFontWeight());

        FindReplaceEngine.SearchResult r = FxTestUtil.callAndWait(() ->
                engine.replaceSelection("fett", "FETT", opts()));
        flush();
        assertNotNull(r);
        assertEquals("FETT und normal", text());
        assertEquals(FontWeight.BOLD, textDecorationAt(0).getFontWeight());
    }

    @Test
    @Disabled("format preservation fix pending")
    void replaceSelection_rejectsParagraphBoundary() {
        setText("abc\ndef");
        FxTestUtil.runAndWait(() -> {
            var vm = ((RichTextAreaSkin) rta.getSkin()).getViewModel();
            vm.setSelection(new Selection(0, 7));
        });
        flush();
        FindReplaceEngine.SearchResult r = FxTestUtil.callAndWait(() ->
                engine.replaceSelection("abc\ndef", "XYZ", opts()));
        flush();
        assertNull(r);
        assertEquals("abc\ndef", text());
    }

    @Test
    @Disabled("format preservation fix pending")
    void replaceAll_preservesBoldDecoration() {
        setText("fett und fett nochmals");
        FxTestUtil.runAndWait(() -> {
            var vm = ((RichTextAreaSkin) rta.getSkin()).getViewModel();
            vm.setSelection(new Selection(0, 4));
            rta.getActionFactory().decorate(
                    TextDecoration.builder().presets().fontWeight(FontWeight.BOLD).build())
                    .execute(new ActionEvent());
            vm.setSelection(new Selection(9, 13));
            rta.getActionFactory().decorate(
                    TextDecoration.builder().presets().fontWeight(FontWeight.BOLD).build())
                    .execute(new ActionEvent());
        });
        flush();
        assertEquals(FontWeight.BOLD, textDecorationAt(0).getFontWeight());
        assertEquals(FontWeight.BOLD, textDecorationAt(9).getFontWeight());

        int count = FxTestUtil.callAndWait(() -> engine.replaceAll("fett", "FETT", opts()));
        flush();
        assertEquals(2, count);
        assertEquals("FETT und FETT nochmals", text());
        assertEquals(FontWeight.BOLD, textDecorationAt(0).getFontWeight());
        assertEquals(FontWeight.BOLD, textDecorationAt(9).getFontWeight());
    }

    @Test
    @Disabled("format preservation fix pending")
    void replaceAll_skipsParagraphBoundary() {
        setText("abc\ndef abc\ndef");
        int count = FxTestUtil.callAndWait(() -> engine.replaceAll("abc\ndef", "XYZ", opts()));
        flush();
        assertEquals(0, count);
        assertEquals("abc\ndef abc\ndef", text());
    }

    private Document document() {
        FxTestUtil.runAndWait(() -> rta.getActionFactory().save().execute(new ActionEvent()));
        flush();
        return rta.getDocument();
    }

    private TextDecoration textDecorationAt(int index) {
        Document doc = document();
        for (var dm : doc.getDecorations()) {
            if (index >= dm.getStart() && index < dm.getStart() + dm.getLength()) {
                return (TextDecoration) dm.getDecoration();
            }
        }
        for (var dm : doc.getDecorations()) {
            if (dm.getStart() == index) {
                return (TextDecoration) dm.getDecoration();
            }
        }
        throw new AssertionError("no decoration at index " + index + " in " + doc);
    }
}
