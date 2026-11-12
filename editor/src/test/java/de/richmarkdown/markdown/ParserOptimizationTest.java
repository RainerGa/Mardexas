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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParserOptimizationTest {
    @Test void boundedNestedScansKeepLinkAndFormattingState() {
        var model = new MarkdownParser().parse("**[link](https://example.org)** and [*kursiv*](https://a) <u>under **bold**</u>");
        var inlines = ((ParagraphBlock) model.getBlocks().getFirst()).getInlines();
        assertTrue(inlines.stream().anyMatch(inline -> inline.getText().equals("link") && inline.isBold()
                && "https://example.org".equals(inline.getUrl())));
        assertTrue(inlines.stream().anyMatch(inline -> inline.getText().equals("kursiv") && inline.isItalic()
                && "https://a".equals(inline.getUrl())));
        assertTrue(inlines.stream().anyMatch(inline -> inline.getText().equals("bold") && inline.isBold() && inline.isUnderline()));
    }
    @Test void manyUnclosedLinkMarkersRemainLiteral() {
        String source = "[offen <tag _ ".repeat(20000).strip();
        assertEquals(source, new MarkdownParser().parseInto(source).getText());
    }
    @Test void cancelledParsingStopsWithoutPublishingPartialResults() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(java.util.concurrent.CancellationException.class, () -> new MarkdownParser().parse("body"));
        } finally { Thread.interrupted(); }
    }
}
