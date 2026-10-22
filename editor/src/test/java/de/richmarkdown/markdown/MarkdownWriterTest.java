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

import com.gluonhq.richtextarea.model.DecorationModel;
import com.gluonhq.richtextarea.model.Document;
import com.gluonhq.richtextarea.model.ParagraphDecoration;
import com.gluonhq.richtextarea.model.TextDecoration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TDD Phase 2: MarkdownWriter – Document → Markdown (PROJEKTPLAN 5.2, 5.3).
 *
 * Reine Unit-Tests: Dokumente werden direkt über die Modell-Klassen der
 * Bibliothek aufgebaut (kein FX-Toolkit nötig).
 */
class MarkdownWriterTest {

    private final MarkdownWriter writer = new MarkdownWriter();

    // ---------- Test-Helfer ----------

    private Document doc(String text, DecorationModel... models) {
        return new Document(text, List.of(models), 0);
    }

    private DecorationModel dm(int start, int length, TextDecoration td, ParagraphDecoration pd) {
        return new DecorationModel(start, length, td, pd);
    }

    private TextDecoration normal() {
        return TextDecoration.builder().presets().build();
    }

    private TextDecoration bold() {
        return TextDecoration.builder().presets().fontWeight(FontWeight.BOLD).build();
    }

    private TextDecoration italic() {
        return TextDecoration.builder().presets().fontPosture(FontPosture.ITALIC).build();
    }

    private TextDecoration underline() {
        return TextDecoration.builder().presets().underline(true).build();
    }

    private TextDecoration strikethrough() {
        return TextDecoration.builder().presets().strikethrough(true).build();
    }

    private TextDecoration heading(int level) {
        return TextDecoration.builder().presets()
                .fontSize(MarkdownStyles.headingSize(level))
                .fontWeight(FontWeight.BOLD)
                .build();
    }

    private TextDecoration codeBlockDecoration() {
        return TextDecoration.builder().presets()
                .fontFamily(MarkdownStyles.MONOSPACED)
                .background(MarkdownStyles.CODE_BLOCK_BACKGROUND)
                .build();
    }

    private TextDecoration inlineCode() {
        return TextDecoration.builder().presets()
                .fontFamily(MarkdownStyles.MONOSPACED)
                .build();
    }

    private ParagraphDecoration paragraph() {
        return ParagraphDecoration.builder().presets().build();
    }

    private ParagraphDecoration bulletList(int level) {
        return ParagraphDecoration.builder().presets()
                .graphicType(ParagraphDecoration.GraphicType.BULLETED_LIST)
                .indentationLevel(level)
                .build();
    }

    private ParagraphDecoration numberedList(int level) {
        return numberedList(level, 1);
    }

    private ParagraphDecoration numberedList(int level, int startNumber) {
        return ParagraphDecoration.builder().presets()
                .graphicType(ParagraphDecoration.GraphicType.NUMBERED_LIST)
                .indentationLevel(level)
                .startNumber(startNumber)
                .build();
    }

    private ParagraphDecoration quote(int level) {
        return ParagraphDecoration.builder().presets().indentationLevel(level).build();
    }

    // ---------- Absätze ----------

    @Test
    void writesPlainParagraph() {
        Document document = doc("Hello world", dm(0, 11, normal(), paragraph()));
        assertEquals("Hello world", writer.write(document));
    }

    @Test
    void writesMultipleParagraphsSeparatedByBlankLine() {
        Document document = doc("first\n\nsecond",
                dm(0, 6, normal(), paragraph()),
                dm(6, 1, normal(), paragraph()),
                dm(7, 6, normal(), paragraph()));
        assertEquals("first\n\nsecond", writer.write(document));
    }

    @Test
    void writesConsecutiveEditorParagraphsOnSeparateLines() {
        Document document = doc("first\nsecond",
                dm(0, 6, normal(), paragraph()),
                dm(6, 6, normal(), paragraph()));
        assertEquals("first\nsecond", writer.write(document));
    }

    @Test
    void keepsSingleTrailingNewline() {
        // Ein einzelner trailing leerer Absatz wird als abschließendes Newline
        // erhalten (konventioneller Dateiabschluss)
        Document document = doc("text\n",
                dm(0, 5, normal(), paragraph()),
                dm(5, 1, normal(), paragraph()));
        assertEquals("text\n", writer.write(document));
    }

    @Test
    void normalizesMultipleTrailingEmptyParagraphs() {
        Document document = doc("text\n\n",
                dm(0, 5, normal(), paragraph()),
                dm(5, 1, normal(), paragraph()),
                dm(6, 1, normal(), paragraph()));
        assertEquals("text\n", writer.write(document));
    }

    @Test
    void writesEmptyDocumentAsEmptyString() {
        assertEquals("", writer.write(new Document()));
    }

    // ---------- Überschriften ----------

    @Test
    void writesHeadings1To6() {
        for (int level = 1; level <= 6; level++) {
            String text = "Title";
            Document document = doc(text, dm(0, text.length(), heading(level), paragraph()));
            assertEquals("#".repeat(level) + " Title", writer.write(document),
                    "heading level " + level);
        }
    }

    @Test
    void headingRequiresBoldAndLeftAlignment() {
        // Große Schrift ohne bold ist keine Überschrift → normaler Absatz
        Document document = doc("Title", dm(0, 5,
                TextDecoration.builder().presets().fontSize(32).build(), paragraph()));
        assertEquals("Title", writer.write(document));
    }

    // ---------- Inline-Formatierung ----------

    @Test
    void writesBoldText() {
        Document document = doc("a bold b",
                dm(0, 2, normal(), paragraph()),
                dm(2, 4, bold(), paragraph()),
                dm(6, 2, normal(), paragraph()));
        assertEquals("a **bold** b", writer.write(document));
    }

    @Test
    void writesItalicText() {
        Document document = doc("a italic b",
                dm(0, 2, normal(), paragraph()),
                dm(2, 6, italic(), paragraph()),
                dm(8, 2, normal(), paragraph()));
        assertEquals("a *italic* b", writer.write(document));
    }

    @Test
    void writesUnderlineTextAsHtml() {
        Document document = doc("a under b",
                dm(0, 2, normal(), paragraph()),
                dm(2, 5, underline(), paragraph()),
                dm(7, 2, normal(), paragraph()));
        assertEquals("a <u>under</u> b", writer.write(document));
    }

    @Test
    void writesStrikethroughText() {
        Document document = doc("a strike b",
                dm(0, 2, normal(), paragraph()),
                dm(2, 6, strikethrough(), paragraph()),
                dm(8, 2, normal(), paragraph()));
        assertEquals("a ~~strike~~ b", writer.write(document));
    }

    @Test
    void writesBoldItalicCombination() {
        Document document = doc("both",
                dm(0, 4, TextDecoration.builder().presets()
                        .fontWeight(FontWeight.BOLD).fontPosture(FontPosture.ITALIC).build(),
                        paragraph()));
        assertEquals("***both***", writer.write(document));
    }

    @Test
    void writesInlineCode() {
        Document document = doc("a code b",
                dm(0, 2, normal(), paragraph()),
                dm(2, 4, inlineCode(), paragraph()),
                dm(6, 2, normal(), paragraph()));
        assertEquals("a `code` b", writer.write(document));
    }

    @Test
    void writesLink() {
        Document document = doc("link",
                dm(0, 4, TextDecoration.builder().presets()
                        .url("https://example.com").build(), paragraph()));
        assertEquals("[link](https://example.com)", writer.write(document));
    }

    // ---------- Listen ----------

    @Test
    void writesBulletListLevel1() {
        Document document = doc("item", dm(0, 4, normal(), bulletList(1)));
        assertEquals("- item", writer.write(document));
    }

    @Test
    void writesBulletListLevel3() {
        Document document = doc("item", dm(0, 4, normal(), bulletList(3)));
        assertEquals("    - item", writer.write(document));
    }

    @Test
    void writesNumberedListWithIncrementingNumbers() {
        Document document = doc("first\nsecond\nthird",
                dm(0, 6, normal(), numberedList(1)),
                dm(6, 7, normal(), numberedList(1)),
                dm(13, 5, normal(), numberedList(1)));
        assertEquals("1. first\n2. second\n3. third", writer.write(document));
    }

    @Test
    void writesNestedLists() {
        Document document = doc("top\nnested",
                dm(0, 4, normal(), bulletList(1)),
                dm(4, 7, normal(), bulletList(2)));
        assertEquals("- top\n  - nested", writer.write(document));
    }

    // ---------- Zitat ----------

    @Test
    void writesQuote() {
        Document document = doc("quoted", dm(0, 6, normal(), quote(1)));
        assertEquals("> quoted", writer.write(document));
    }

    @Test
    void writesNestedQuote() {
        Document document = doc("quoted", dm(0, 6, normal(), quote(2)));
        assertEquals("> > quoted", writer.write(document));
    }

    // ---------- Code-Block ----------

    @Test
    void writesCodeBlock() {
        Document document = doc("code line", dm(0, 9, codeBlockDecoration(), paragraph()));
        assertEquals("```\ncode line\n```", writer.write(document));
    }

    @Test
    void writesMultiLineCodeBlock() {
        Document document = doc("line1\nline2",
                dm(0, 6, codeBlockDecoration(), paragraph()),
                dm(6, 6, codeBlockDecoration(), paragraph()));
        assertEquals("```\nline1\nline2\n```", writer.write(document));
    }

    // ---------- Horizontal Rule ----------

    @Test
    void writesHorizontalRule() {
        // HR: Absatz nur aus Leerzeichen, vollständig durchgestrichen
        String spaces = "   ";
        Document document = doc(spaces, dm(0, 3, strikethrough(), paragraph()));
        assertEquals("---", writer.write(document));
    }

    // ---------- Blocktrennung ----------

    @Test
    void separatesListItemsBySingleNewline() {
        Document document = doc("a\nb",
                dm(0, 2, normal(), bulletList(1)),
                dm(2, 2, normal(), bulletList(1)));
        assertEquals("- a\n- b", writer.write(document));
    }

    @Test
    void separatesDistinctListsByBlankLine() {
        // Leerer Absatz zwischen zwei Listen → getrennte Listen
        Document document = doc("a\n\nb",
                dm(0, 2, normal(), bulletList(1)),
                dm(2, 1, normal(), paragraph()),
                dm(3, 2, normal(), bulletList(1)));
        assertEquals("- a\n\n- b", writer.write(document));
    }

    @Test
    void separatesListFromParagraphByBlankLine() {
        Document document = doc("a\ntext",
                dm(0, 2, normal(), bulletList(1)),
                dm(2, 5, normal(), paragraph()));
        assertEquals("- a\n\ntext", writer.write(document));
    }

    @Test
    void separatesHeadingFromParagraphByBlankLine() {
        Document document = doc("Title\nbody",
                dm(0, 6, heading(1), paragraph()),
                dm(6, 5, normal(), paragraph()));
        assertEquals("# Title\n\nbody", writer.write(document));
    }

    @Test
    void separatesQuoteFromParagraphByBlankLine() {
        Document document = doc("quoted\ntext",
                dm(0, 7, normal(), quote(1)),
                dm(7, 5, normal(), paragraph()));
        assertEquals("> quoted\n\ntext", writer.write(document));
    }

    @Test
    void separatesCodeBlockFromParagraphByBlankLine() {
        Document document = doc("code\ntext",
                dm(0, 5, codeBlockDecoration(), paragraph()),
                dm(5, 5, normal(), paragraph()));
        assertEquals("```\ncode\n```\n\ntext", writer.write(document));
    }

    // ---------- Escaping ----------

    @Test
    void unmatchedMarkersAreNotEscaped() {
        // Ungematchte Inline-Marker round-trippen identisch (kein Escaping nötig)
        Document document = doc("a * b _ c", dm(0, 9, normal(), paragraph()));
        assertEquals("a * b _ c", writer.write(document));
    }

    @Test
    void escapesLeadingHash() {
        Document document = doc("# not heading", dm(0, 12, normal(), paragraph()));
        assertEquals("\\# not heading", writer.write(document));
    }

    @Test
    void escapesLeadingListMarker() {
        Document document = doc("- not list", dm(0, 9, normal(), paragraph()));
        assertEquals("\\- not list", writer.write(document));
    }

    // ---------- Modell-Rundlauf ----------

    @Test
    void writeAcceptsMarkdownModel() {
        MarkdownModel model = MarkdownModel.of(
                new ParagraphBlock(List.of(Inline.text("hello"))));
        assertEquals("hello", writer.write(model));
    }

    @Test
    void toModelProducesExpectedBlocks() {
        Document document = doc("Title", dm(0, 5, heading(1), paragraph()));
        MarkdownModel model = writer.toModel(document);
        assertEquals(1, model.getBlocks().size());
        assertEquals(HeadingBlock.class, model.getBlocks().get(0).getClass());
        assertEquals(1, ((HeadingBlock) model.getBlocks().get(0)).getLevel());
    }

    @Test
    void consecutiveListItemsFormOneList() {
        Document document = doc("a\nb",
                dm(0, 2, normal(), bulletList(1)),
                dm(2, 2, normal(), bulletList(1)));
        MarkdownModel model = writer.toModel(document);
        assertEquals(2, model.getBlocks().size());
    }

    @Test
    void numberedListWithStartNumber() {
        // Create a document with a numbered list starting at 3
        Document document = doc("First\nSecond",
                dm(0, 6, normal(), numberedList(1, 3)),
                dm(6, 7, normal(), numberedList(1, 3)));
        String written = writer.write(document);
        assertTrue(written.contains("3. First"), "Should start at 3: " + written);
        assertTrue(written.contains("4. Second"), "Should continue at 4: " + written);
    }

    @Test
    void emptyParagraphsBecomeEmptyBlocks() {
        Document document = doc("a\n\nb",
                dm(0, 2, normal(), paragraph()),
                dm(2, 1, normal(), paragraph()),
                dm(3, 2, normal(), paragraph()));
        MarkdownModel model = writer.toModel(document);
        assertEquals(3, model.getBlocks().size());
    }

    @Test
    void inlineRunsAreMerged() {
        Document document = doc("bold text",
                dm(0, 4, bold(), paragraph()),
                dm(4, 5, bold(), paragraph()));
        MarkdownModel model = writer.toModel(document);
        ParagraphBlock block = (ParagraphBlock) model.getBlocks().get(0);
        assertEquals(1, block.getInlines().size());
        assertEquals("bold text", block.getInlines().get(0).getText());
    }

    @Test
    void zeroWidthCharactersAreStripped() {
        Document document = doc("a\u200bb", dm(0, 3, normal(), paragraph()));
        assertEquals("ab", writer.write(document));
    }

    @Test
    void helperBuildsDecorationList() {
        List<DecorationModel> models = new ArrayList<>();
        models.add(dm(0, 1, normal(), paragraph()));
        assertEquals(1, models.size());
    }
}
