package de.richmarkdown.markdown;

import com.gluonhq.richtextarea.model.DecorationModel;
import com.gluonhq.richtextarea.model.Document;
import com.gluonhq.richtextarea.model.ParagraphDecoration;
import com.gluonhq.richtextarea.model.TextDecoration;
import javafx.scene.text.FontWeight;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD Phase 3: MarkdownParser – Markdown → MarkdownModel/Document
 * (PROJEKTPLAN 5.2).
 */
class MarkdownParserTest {

    private final MarkdownParser parser = new MarkdownParser();
    private final MarkdownWriter writer = new MarkdownWriter();

    // ---------- Absätze ----------

    @Test
    void parsesPlainParagraph() {
        MarkdownModel model = parser.parse("Hello world");
        assertEquals(1, model.getBlocks().size());
        ParagraphBlock block = (ParagraphBlock) model.getBlocks().get(0);
        assertEquals(1, block.getInlines().size());
        assertEquals("Hello world", block.getInlines().get(0).getText());
    }

    @Test
    void parsesMultipleParagraphs() {
        // Die Leerzeile wird als leerer Absatz-Block abgebildet (der Writer
        // nutzt ihn zur Absatztrennung)
        MarkdownModel model = parser.parse("first\n\nsecond");
        assertEquals(3, model.getBlocks().size());
    }

    @Test
    void parsesSingleNewlinesAsSeparateEditorLines() {
        MarkdownModel model = parser.parse("first\nsecond");
        assertEquals(2, model.getBlocks().size());
        assertEquals("first\nsecond", parser.parseInto("first\nsecond").getText());
    }

    @Test
    void parsesEmptyDocument() {
        MarkdownModel model = parser.parse("");
        assertTrue(model.getBlocks().isEmpty());
    }

    // ---------- Überschriften ----------

    @Test
    void parsesHeadings1To6() {
        for (int level = 1; level <= 6; level++) {
            MarkdownModel model = parser.parse("#".repeat(level) + " Title");
            assertEquals(1, model.getBlocks().size(), "level " + level);
            HeadingBlock block = (HeadingBlock) model.getBlocks().get(0);
            assertEquals(level, block.getLevel());
            assertEquals("Title", block.getInlines().get(0).getText());
        }
    }

    @Test
    void headingRequiresSpaceAfterHash() {
        MarkdownModel model = parser.parse("#notheading");
        assertTrue(model.getBlocks().get(0) instanceof ParagraphBlock);
    }

    // ---------- Inline-Formatierung ----------

    @Test
    void parsesBold() {
        ParagraphBlock block = singleParagraph(parser.parse("a **bold** b"));
        assertEquals(3, block.getInlines().size());
        assertEquals("bold", block.getInlines().get(1).getText());
        assertTrue(block.getInlines().get(1).isBold());
        assertFalse(block.getInlines().get(0).isBold());
    }

    @Test
    void parsesItalic() {
        ParagraphBlock block = singleParagraph(parser.parse("a *it* b"));
        assertTrue(block.getInlines().get(1).isItalic());
        assertEquals("it", block.getInlines().get(1).getText());
    }

    @Test
    void parsesBoldItalic() {
        ParagraphBlock block = singleParagraph(parser.parse("***bi***"));
        Inline inline = block.getInlines().get(0);
        assertTrue(inline.isBold());
        assertTrue(inline.isItalic());
    }

    @Test
    void parsesStrikethrough() {
        ParagraphBlock block = singleParagraph(parser.parse("~~s~~"));
        assertTrue(block.getInlines().get(0).isStrikethrough());
    }

    @Test
    void parsesInlineCode() {
        ParagraphBlock block = singleParagraph(parser.parse("a `code` b"));
        assertTrue(block.getInlines().get(1).isCode());
        assertEquals("code", block.getInlines().get(1).getText());
    }

    @Test
    void parsesLink() {
        ParagraphBlock block = singleParagraph(parser.parse("[text](https://example.com)"));
        Inline inline = block.getInlines().get(0);
        assertEquals("text", inline.getText());
        assertEquals("https://example.com", inline.getUrl());
    }

    @Test
    void parsesUnderline() {
        ParagraphBlock block = singleParagraph(parser.parse("<u>under</u>"));
        assertTrue(block.getInlines().get(0).isUnderline());
        assertEquals("under", block.getInlines().get(0).getText());
    }

    @Test
    void parsesEscapedCharacters() {
        ParagraphBlock block = singleParagraph(parser.parse("a \\* b"));
        assertEquals("a * b", block.getInlines().get(0).getText());
        assertFalse(block.getInlines().get(0).isBold());
    }

    @Test
    void parsesNestedFormatting() {
        // "**a *b* c**" → fetter Lauf, fett-kursiver Einschub, fetter Lauf
        ParagraphBlock block = singleParagraph(parser.parse("**a *b* c**"));
        assertEquals(3, block.getInlines().size());
        assertTrue(block.getInlines().get(0).isBold());
        assertFalse(block.getInlines().get(0).isItalic());
        assertTrue(block.getInlines().get(1).isBold());
        assertTrue(block.getInlines().get(1).isItalic());
        assertEquals("b", block.getInlines().get(1).getText());
        assertTrue(block.getInlines().get(2).isBold());
        assertFalse(block.getInlines().get(2).isItalic());
    }

    // ---------- Listen ----------

    @Test
    void parsesBulletList() {
        MarkdownModel model = parser.parse("- a\n- b");
        assertEquals(2, model.getBlocks().size());
        assertTrue(model.getBlocks().get(0) instanceof ListItemBlock);
        assertEquals(1, ((ListItemBlock) model.getBlocks().get(0)).getLevel());
        assertFalse(((ListItemBlock) model.getBlocks().get(0)).isNumbered());
    }

    @Test
    void parsesNumberedList() {
        MarkdownModel model = parser.parse("1. a\n2. b");
        assertTrue(((ListItemBlock) model.getBlocks().get(0)).isNumbered());
        assertEquals(2, model.getBlocks().size());
    }

    @Test
    void parsesNestedList() {
        MarkdownModel model = parser.parse("- a\n  - b");
        assertEquals(1, ((ListItemBlock) model.getBlocks().get(0)).getLevel());
        assertEquals(2, ((ListItemBlock) model.getBlocks().get(1)).getLevel());
    }

    @Test
    void parsesStarBullet() {
        MarkdownModel model = parser.parse("* a");
        assertTrue(model.getBlocks().get(0) instanceof ListItemBlock);
    }

    // ---------- Zitate ----------

    @Test
    void parsesQuote() {
        MarkdownModel model = parser.parse("> quoted");
        assertTrue(model.getBlocks().get(0) instanceof QuoteBlock);
        QuoteBlock quote = (QuoteBlock) model.getBlocks().get(0);
        assertEquals(1, quote.getChildren().size());
    }

    @Test
    void parsesNestedQuote() {
        MarkdownModel model = parser.parse("> > deep");
        QuoteBlock quote = (QuoteBlock) model.getBlocks().get(0);
        assertTrue(quote.getChildren().get(0) instanceof QuoteBlock);
    }

    @Test
    void parsesMultiLineQuote() {
        // Jede Zitat-Zeile wird ein eigener Absatz (Round-Trip-Stabilität;
        // das Document-Modell kann keine Zeilenumbrüche im Absatz abbilden)
        MarkdownModel model = parser.parse("> a\n> b");
        assertEquals(1, model.getBlocks().size());
        assertEquals(2, ((QuoteBlock) model.getBlocks().get(0)).getChildren().size());
    }

    // ---------- Code-Block ----------

    @Test
    void parsesCodeBlock() {
        MarkdownModel model = parser.parse("```\ncode line\n```");
        assertEquals(1, model.getBlocks().size());
        CodeBlockBlock block = (CodeBlockBlock) model.getBlocks().get(0);
        assertEquals(List.of("code line"), block.getLines());
    }

    @Test
    void parsesMultiLineCodeBlock() {
        MarkdownModel model = parser.parse("```\nl1\nl2\n```");
        assertEquals(List.of("l1", "l2"), ((CodeBlockBlock) model.getBlocks().get(0)).getLines());
    }

    // ---------- Horizontal Rule ----------

    @Test
    void parsesHorizontalRule() {
        assertTrue(parser.parse("---").getBlocks().get(0) instanceof HorizontalRuleBlock);
        assertTrue(parser.parse("***").getBlocks().get(0) instanceof HorizontalRuleBlock);
        assertTrue(parser.parse("___").getBlocks().get(0) instanceof HorizontalRuleBlock);
    }

    // ---------- parseInto: Document ----------

    @Test
    void parseInto_buildsDocumentWithText() {
        Document document = parser.parseInto("Hello");
        assertEquals("Hello", document.getText());
        assertFalse(document.getDecorations().isEmpty());
    }

    @Test
    void parseInto_headingDocumentHasHeadingDecoration() {
        Document document = parser.parseInto("# Title");
        TextDecoration td = (TextDecoration) document.getDecorations().get(0).getDecoration();
        assertEquals(MarkdownStyles.headingSize(1), td.getFontSize());
        assertEquals(FontWeight.BOLD, td.getFontWeight());
    }

    @Test
    void parseInto_boldDocumentHasBoldRun() {
        Document document = parser.parseInto("a **b** c");
        // Runs: "a " [0,2), "b" [2,3) bold, " c" [3,5)
        boolean foundBold = document.getDecorations().stream()
                .anyMatch(dm -> dm.getDecoration() instanceof TextDecoration td
                        && td.getFontWeight() == FontWeight.BOLD);
        assertTrue(foundBold, "bold run expected in " + document);
    }

    @Test
    void parseInto_listDocumentHasListDecoration() {
        Document document = parser.parseInto("- item");
        ParagraphDecoration pd = document.getDecorations().get(0).getParagraphDecoration();
        assertEquals(ParagraphDecoration.GraphicType.BULLETED_LIST, pd.getGraphicType());
        assertEquals(1, pd.getIndentationLevel());
    }

    @Test
    void parseInto_quoteDocumentHasIndentation() {
        Document document = parser.parseInto("> quoted");
        ParagraphDecoration pd = document.getDecorations().get(0).getParagraphDecoration();
        assertEquals(1, pd.getIndentationLevel());
        assertEquals(ParagraphDecoration.GraphicType.NONE, pd.getGraphicType());
    }

    @Test
    void parseInto_codeBlockDocumentHasBackgroundMarker() {
        Document document = parser.parseInto("```\ncode\n```");
        TextDecoration td = (TextDecoration) document.getDecorations().get(0).getDecoration();
        assertTrue(MarkdownStyles.isCodeBlockBackground(td.getBackground()));
        assertEquals(MarkdownStyles.MONOSPACED, td.getFontFamily());
    }

    @Test
    void parseInto_multipleParagraphsHaveNewlineSeparator() {
        Document document = parser.parseInto("first\n\nsecond");
        assertEquals("first\n\nsecond", document.getText());
    }

    @Test
    void parseInto_decorationsCoverFullText() {
        Document document = parser.parseInto("# Title\n\npara **bold**");
        int covered = document.getDecorations().stream()
                .mapToInt(DecorationModel::getLength).sum();
        assertEquals(document.getText().length(), covered,
                "decorations must cover full text: " + document);
    }

    // ---------- Round-Trip mit dem Writer ----------

    @Test
    void roundTrip_plainParagraph() {
        assertRoundTrip("Hello world");
    }

    @Test
    void roundTrip_headings() {
        for (int level = 1; level <= 6; level++) {
            assertRoundTrip("#".repeat(level) + " Title");
        }
    }

    @Test
    void roundTrip_inlineFormatting() {
        assertRoundTrip("a **bold** b");
        assertRoundTrip("a *it* b");
        assertRoundTrip("a ~~s~~ b");
        assertRoundTrip("a `code` b");
        assertRoundTrip("a <u>u</u> b");
        assertRoundTrip("[text](https://example.com)");
    }

    @Test
    void roundTrip_lists() {
        assertRoundTrip("- a\n- b");
        assertRoundTrip("1. a\n2. b");
        assertRoundTrip("- a\n  - b");
    }

    @Test
    void roundTrip_quote() {
        assertRoundTrip("> quoted");
        assertRoundTrip("> > deep");
    }

    @Test
    void roundTrip_codeBlock() {
        assertRoundTrip("```\ncode\n```");
    }

    @Test
    void roundTrip_horizontalRule() {
        assertRoundTrip("---");
    }

    @Test
    void roundTrip_mixedDocument() {
        assertRoundTrip("# Title\n\npara text\n\n- a\n- b\n\n> quote\n\n```\ncode\n```");
    }

    // ---------- Helfer ----------

    private ParagraphBlock singleParagraph(MarkdownModel model) {
        assertEquals(1, model.getBlocks().size(), "expected exactly one block");
        return (ParagraphBlock) model.getBlocks().get(0);
    }

    private void assertRoundTrip(String markdown) {
        Document document = parser.parseInto(markdown);
        String written = writer.write(document);
        assertEquals(markdown, written, "round trip failed");
    }
}
