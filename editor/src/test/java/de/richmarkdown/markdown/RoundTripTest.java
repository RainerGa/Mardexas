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

import com.gluonhq.richtextarea.model.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TDD Phase 4: Round-Trip-Testmatrix (PROJEKTPLAN 6.2, 7.1).
 *
 * Verbindliche Gleichung in beide Richtungen:
 * <ul>
 *   <li>{@code write(parse(md)) == md}</li>
 *   <li>{@code parse(write(md)) == md}</li>
 * </ul>
 *
 * Die Matrix deckt alle Kanon-Dokumenttypen aus 7.1 ab. Tabellen und Bilder
 * folgen mit Phase 6 (Document-Rebuild).
 */
class RoundTripTest {

    private final MarkdownParser parser = new MarkdownParser();
    private final MarkdownWriter writer = new MarkdownWriter();

    // ---------- Kanon-Dokumente der Matrix (7.1) ----------

    private static final String[] CANONICAL_DOCUMENTS = {
            // Absätze
            "Hello world",
            "first\nsecond",
            "first\n\nsecond",
            "first\n\nsecond\n\nthird",
            // Überschriften
            "# H1",
            "## H2",
            "### H3",
            "#### H4",
            "##### H5",
            "###### H6",
            // Hinweis: explizites Fett innerhalb von Überschriften wird
            // normalisiert (Überschriften sind implizit fett) – daher hier nur kursiv
            "# Heading with *italic*",
            // Inline-Formatierung
            "a **bold** b",
            "a *italic* b",
            "a ***bold italic*** b",
            "a ~~strike~~ b",
            "a `code` b",
            "a <u>under</u> b",
            "**bold at start**",
            "**bold at end**",
            "[link text](https://example.com)",
            "see [link](https://example.org) here",
            // Listen
            "- a\n- b",
            "- a\n- b\n- c",
            "1. a\n2. b",
            "1. a\n2. b\n3. c",
            "- a\n  - b",
            "- a\n  - b\n    - c",
            "1. a\n   1. b",
            "- a\n\n- b",
            // Zitate
            "> quoted",
            "> > deep",
            "> quote with **bold**",
            // Code-Blöcke
            "```\ncode\n```",
            "```\nline1\nline2\n```",
            "```\ncode with **stars**\n```",
            // Horizontal Rule
            "---",
            // Escaping (nur Zeilenanfang; Inline-Escapes werden normalisiert)
            "a * b",
            "\\# not heading",
            "\\- not list",
            // Gemischte Dokumente
            "# Title\n\npara text",
            "# Title\n\npara text\n\n- a\n- b",
            "para\n\n- a\n- b\n\npara2",
            "# Title\n\n> quote\n\n- a\n- b\n\n```\ncode\n```",
            "text\n\n---\n\nmore text",
            "- a\n- b\n\n---\n\n- c",
    };

    @Test
    void writeOfParseIsIdentity() {
        for (String markdown : CANONICAL_DOCUMENTS) {
            Document document = parser.parseInto(markdown);
            String written = writer.write(document);
            assertEquals(markdown, written, "write(parse(md)) failed for: " + markdown);
        }
    }

    @Test
    void parseOfWriteIsIdentity() {
        for (String markdown : CANONICAL_DOCUMENTS) {
            Document document = parser.parseInto(markdown);
            String written = writer.write(document);
            Document reparsed = parser.parseInto(written);
            String rewritten = writer.write(reparsed);
            assertEquals(written, rewritten, "parse(write(md)) not stable for: " + markdown);
        }
    }

    @Test
    void documentTextIsStableAcrossRoundTrip() {
        for (String markdown : CANONICAL_DOCUMENTS) {
            Document first = parser.parseInto(markdown);
            Document second = parser.parseInto(writer.write(first));
            assertEquals(first.getText(), second.getText(),
                    "document text changed for: " + markdown);
        }
    }

    // ---------- Grenzfälle ----------

    @Test
    void emptyDocumentRoundTrips() {
        assertEquals("", writer.write(parser.parseInto("")));
    }

    @Test
    void whitespaceOnlyDocumentRoundTrips() {
        assertEquals("", writer.write(parser.parseInto("   ")));
    }

    @Test
    void singleCharacterRoundTrips() {
        assertEquals("x", writer.write(parser.parseInto("x")));
    }

    @Test
    void unicodeTextRoundTrips() {
        String md = "Überraschung: naïve café — 日本語 🎉";
        assertEquals(md, writer.write(parser.parseInto(md)));
    }

    @Test
    void longParagraphRoundTrips() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("word").append(i).append(' ');
        }
        String md = sb.toString().trim();
        assertEquals(md, writer.write(parser.parseInto(md)));
    }

    @Test
    void manyListItemsRoundTrip() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append("- item ").append(i);
        }
        String md = sb.toString();
        assertEquals(md, writer.write(parser.parseInto(md)));
    }

    @Test
    void deeplyNestedListRoundTrips() {
        String md = "- a\n  - b\n    - c\n      - d";
        assertEquals(md, writer.write(parser.parseInto(md)));
    }

    @Test
    void deeplyNestedQuoteRoundTrips() {
        String md = "> > > deep";
        assertEquals(md, writer.write(parser.parseInto(md)));
    }

    @Test
    void emptyCodeBlockRoundTrips() {
        assertEquals("```\n\n```", writer.write(parser.parseInto("```\n\n```")));
    }

    @Test
    void codeBlockWithBlankLinesRoundTrips() {
        String md = "```\na\n\nb\n```";
        assertEquals(md, writer.write(parser.parseInto(md)));
    }

    @Test
    void headingWithTrailingHashesRoundTrips() {
        // Geschlossene ATX-Überschrift: die '#' am Ende sind Text
        String md = "# Title #";
        assertEquals(md, writer.write(parser.parseInto(md)));
    }

    @Test
    void multipleInlineFormatsInOneParagraph() {
        String md = "**a** *b* ~~c~~ `d` <u>e</u>";
        assertEquals(md, writer.write(parser.parseInto(md)));
    }

    @Test
    void adjacentFormattedRunsRoundTrip() {
        String md = "**a***b*";
        assertEquals(md, writer.write(parser.parseInto(md)));
    }

    @Test
    void nestedBoldItalicRoundTrips() {
        String md = "**a *b* c**";
        assertEquals(md, writer.write(parser.parseInto(md)));
    }

    @Test
    void boldContainingInlineCodeRoundTrips() {
        String md = "**a `code` b**";
        assertEquals(md, writer.write(parser.parseInto(md)));
    }

    @Test
    void linkWithFormattedTextRoundTrips() {
        String md = "[**bold link**](https://example.com)";
        assertEquals(md, writer.write(parser.parseInto(md)));
    }

    @Test
    void listWithFormattedItemsRoundTrips() {
        String md = "- **a**\n- *b*";
        assertEquals(md, writer.write(parser.parseInto(md)));
    }

    @Test
    void quoteWithListRoundTrips() {
        // Blockstrukturen in Zitaten werden als Text erhalten (Round-Trip stabil),
        // aber nicht als Liste gerendert (Document-Modell kann beides nicht abbilden)
        String md = "> - a\n> - b";
        assertEquals(md, writer.write(parser.parseInto(md)));
    }

    @Test
    void quoteWithHeadingRoundTrips() {
        String md = "> # Heading";
        assertEquals(md, writer.write(parser.parseInto(md)));
    }

    @Test
    void quoteWithCodeBlockRoundTrips() {
        String md = "> ```\n> code\n> ```";
        assertEquals(md, writer.write(parser.parseInto(md)));
    }

    @Test
    void largeMixedDocumentRoundTrips() {
        String md = """
                # Project Title

                Introduction paragraph with **bold**, *italic*, ~~strike~~, `code` and a [link](https://example.com).

                ## Section

                - first item
                - second item with **bold**
                - third item
                  - nested item

                1. numbered one
                2. numbered two

                > A quote with *emphasis*
                > > and a nested quote

                ```
                public class Example {
                    // code
                }
                ```

                ---

                Final paragraph.
                """;
        assertEquals(md, writer.write(parser.parseInto(md)));
    }

    @Test
    void repeatedRoundTripsAreStable() {
        String md = "# Title\n\n- a\n- b\n\n> quote\n\n```\ncode\n```";
        Document document = parser.parseInto(md);
        String previous = writer.write(document);
        for (int i = 0; i < 5; i++) {
            String next = writer.write(parser.parseInto(previous));
            assertEquals(previous, next, "instability at iteration " + i);
            previous = next;
        }
    }
}
