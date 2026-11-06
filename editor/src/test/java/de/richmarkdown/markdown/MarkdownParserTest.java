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
import javafx.scene.text.FontWeight;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void parsesSetextHeadingsAndKeepsTheirInlineFormatting() {
        for (String underline : java.util.List.of("=", "===", "-", "--------", "   ---  ")) {
            HeadingBlock heading = (HeadingBlock) parser.parse("A **formatted** heading\n" + underline).getBlocks().getFirst();
            assertEquals(underline.contains("=") ? 1 : 2, heading.getLevel());
            assertEquals("A formatted heading", heading.getInlines().stream().map(Inline::getText)
                    .collect(java.util.stream.Collectors.joining()));
            assertTrue(heading.getInlines().get(1).isBold());
        }
    }

    @Test
    void setextSupportsMultilineHeadingTextAndCanonicalRoundTrip() {
        var document = parser.parseInto("A multiline\nheading\n===\n\nSection\n---");
        assertEquals("# A multiline heading\n\n## Section", writer.write(document));
        assertEquals(writer.write(document), writer.write(parser.parseInto(writer.write(document))));
    }

    @Test
    void setextDoesNotConsumeRulesCodeTablesOrListItems() {
        assertTrue(parser.parse("---").getBlocks().getFirst() instanceof HorizontalRuleBlock);
        assertTrue(parser.parse("Text\n\n---").getBlocks().getLast() instanceof HorizontalRuleBlock);
        assertTrue(parser.parse("Text\n- - -").getBlocks().getLast() instanceof HorizontalRuleBlock);
        assertTrue(parser.parse("- item\n---").getBlocks().getFirst() instanceof ListItemBlock);
        assertTrue(parser.parse("```\nText\n---\n```").getBlocks().getFirst() instanceof CodeBlockBlock);
        assertTrue(parser.parse("| A | B |\n| --- | --- |\n| x | y |").getBlocks().getFirst() instanceof TableBlock);
        assertTrue(parser.parse("Text\n    ===").getBlocks().getFirst() instanceof ParagraphBlock);
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
    void parsesBareUrl() {
        ParagraphBlock block = singleParagraph(parser.parse("https://example.com"));
        assertEquals(1, block.getInlines().size());
        Inline inline = block.getInlines().get(0);
        assertEquals("https://example.com", inline.getText());
        assertEquals("https://example.com", inline.getUrl());
    }

    @Test
    void parsesBareUrlInAngleBrackets() {
        ParagraphBlock block = singleParagraph(parser.parse("<https://example.com>"));
        assertEquals(1, block.getInlines().size());
        Inline inline = block.getInlines().get(0);
        assertEquals("https://example.com", inline.getText());
        assertEquals("https://example.com", inline.getUrl());
    }

    @Test
    void parsesBareUrlHttp() {
        ParagraphBlock block = singleParagraph(parser.parse("http://example.com/path"));
        assertEquals(1, block.getInlines().size());
        Inline inline = block.getInlines().get(0);
        assertEquals("http://example.com/path", inline.getText());
        assertEquals("http://example.com/path", inline.getUrl());
    }

    @Test
    void parsesBareUrlStripsTrailingPunctuation() {
        ParagraphBlock block = singleParagraph(parser.parse("https://example.com."));
        assertEquals(2, block.getInlines().size());
        Inline url = block.getInlines().get(0);
        assertEquals("https://example.com", url.getText());
        assertEquals("https://example.com", url.getUrl());
        Inline punct = block.getInlines().get(1);
        assertEquals(".", punct.getText());
        assertNull(punct.getUrl());
    }

    @Test
    void parsesBareUrlMidSentence() {
        ParagraphBlock block = singleParagraph(parser.parse("see https://example.com today"));
        assertEquals(3, block.getInlines().size());
        assertEquals("see ", block.getInlines().get(0).getText());
        assertEquals("https://example.com", block.getInlines().get(1).getText());
        assertEquals("https://example.com", block.getInlines().get(1).getUrl());
        assertEquals(" today", block.getInlines().get(2).getText());
    }

    @Test
    void doesNotParseUrlInInlineCode() {
        ParagraphBlock block = singleParagraph(parser.parse("`https://example.com`"));
        assertEquals(1, block.getInlines().size());
        assertTrue(block.getInlines().get(0).isCode());
        assertNull(block.getInlines().get(0).getUrl());
    }

    @Test
    void doesNotParseUrlMidWord() {
        ParagraphBlock block = singleParagraph(parser.parse("xhttps://example.com"));
        assertEquals(1, block.getInlines().size());
        assertEquals("xhttps://example.com", block.getInlines().get(0).getText());
        assertNull(block.getInlines().get(0).getUrl());
    }

    @Test
    void stylesBareUrlAsLink() {
        Document doc = parser.parseInto("https://example.com");
        TextDecoration link = null;
        for (DecorationModel dm : doc.getDecorations()) {
            if (dm.getDecoration() instanceof TextDecoration td && td.getURL() != null) {
                link = td;
            }
        }
        assertNotNull(link);
        assertEquals("https://example.com", link.getURL());
        assertEquals("blue", link.getForeground());
        assertTrue(Boolean.TRUE.equals(link.isUnderline()));
    }

    @Test
    void rendersMarkdownImageSyntaxPointingToWebPagesAsLabeledLinks() {
        String markdown = "![Release](https://github.com/freecad/freecad/releases/latest)\n"
                + "![Crowdin](https://crowdin.com/project/freecad)";
        MarkdownModel model = parser.parse(markdown);

        assertEquals(2, model.getBlocks().size());
        assertTrue(model.getBlocks().get(0) instanceof ParagraphBlock);
        ParagraphBlock release = (ParagraphBlock) model.getBlocks().get(0);
        assertEquals("Release", release.getInlines().get(0).getText());
        assertEquals("https://github.com/freecad/freecad/releases/latest",
                release.getInlines().get(0).getUrl());
        assertEquals("[Release](https://github.com/freecad/freecad/releases/latest)\n"
                        + "[Crowdin](https://crowdin.com/project/freecad)",
                writer.write(parser.parseInto(markdown)));
    }

    @Test
    void parsesInlineImageSyntaxToWebpageAsLink() {
        String markdown = "see ![Release](https://example.com) today";
        MarkdownModel model = parser.parse(markdown);
        assertEquals(1, model.getBlocks().size());
        ParagraphBlock block = (ParagraphBlock) model.getBlocks().get(0);
        assertEquals(3, block.getInlines().size());
        assertEquals("see ", block.getInlines().get(0).getText());
        Inline link = block.getInlines().get(1);
        assertEquals("Release", link.getText());
        assertEquals("https://example.com", link.getUrl());
        assertEquals(" today", block.getInlines().get(2).getText());
        // Kein stray '!' als Text-Inline
        assertFalse(block.getInlines().stream()
                .anyMatch(inline -> inline.getText().startsWith("!")));
        assertEquals("see [Release](https://example.com) today",
                writer.write(parser.parseInto(markdown)));
    }

    @Test
    void roundTrip_inlineImageSyntaxToWebpage() {
        String markdown = "see ![Release](https://example.com) today";
        Document first = parser.parseInto(markdown);
        String written = writer.write(first);
        assertEquals("see [Release](https://example.com) today", written);
        Document second = parser.parseInto(written);
        String rewritten = writer.write(second);
        assertEquals(written, rewritten);
    }

    @Test
    void parsesHtmlAnchor() {
        ParagraphBlock block = singleParagraph(
                parser.parse("Visit <a href=\"https://example.com\">our website</a> today"));
        assertEquals(3, block.getInlines().size());
        assertEquals("our website", block.getInlines().get(1).getText());
        assertEquals("https://example.com", block.getInlines().get(1).getUrl());
        TextDecoration linkDecoration = (TextDecoration) parser
                .parseInto("<a href=\"https://example.com\">our website</a>")
                .getDecorations().get(0).getDecoration();
        assertEquals("https://example.com", linkDecoration.getURL());
        assertTrue(Boolean.TRUE.equals(linkDecoration.isUnderline()));
        assertEquals("blue", linkDecoration.getForeground());
        assertEquals("Visit [our website](https://example.com) today",
                writer.write(parser.parseInto(
                        "Visit <a href=\"https://example.com\">our website</a> today")));
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
    void roundTrip_bareUrlsNormalizeToAngleBrackets() {
        // Bare URLs normalisieren zu <url> (kanonische Form)
        assertRoundTripStable("https://example.com");
        assertRoundTripStable("http://example.org/path");
        assertRoundTripStable("see https://example.com today");
    }

    private void assertRoundTripStable(String markdown) {
        Document first = parser.parseInto(markdown);
        String written = writer.write(first);
        Document second = parser.parseInto(written);
        String rewritten = writer.write(second);
        assertEquals(written, rewritten, "round trip not stable for: " + markdown);
    }

    @Test
    void roundTrip_lists() {
        assertRoundTrip("- a\n- b");
        assertRoundTrip("1. a\n2. b");
        assertRoundTrip("- a\n  - b");
    }

    @Test
    void roundTrip_listsWithStartNumber() {
        // Test numbered list with custom start number
        assertRoundTrip("3. First\n4. Second\n5. Third");
        // Test nested list with start number (indentation is 3 spaces for numbered lists)
        assertRoundTrip("1. Level 1\n   5. Nested start at 5\n   6. Next nested");
    }

    @Test
    void roundTrip_quote() {
        assertRoundTrip("> quoted");
        assertRoundTrip("> > deep");
    }

    @Test
    void roundTrip_nestedQuotes() {
        // Test multiple levels of nested quotes
        assertRoundTrip("> Level 1\n> > Level 2\n> > > Level 3");
        // Test quote with content at each level
        assertRoundTrip("> Quote 1\n> > Nested quote\n> Back to level 1");
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
