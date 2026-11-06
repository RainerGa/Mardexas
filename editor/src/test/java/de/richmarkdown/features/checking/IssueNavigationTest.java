package de.richmarkdown.features.checking;

import de.richmarkdown.markdown.MarkdownParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IssueNavigationTest {
    private void assertParagraph(String source, String target, String paragraph) {
        int position = IssueNavigation.exportedParagraph(source, source.indexOf(target), null);
        assertTrue(new MarkdownParser().parseInto(source).getText().substring(position).startsWith(paragraph), "Expected " + paragraph + " at " + position);
    }
    @Test void headingsLinksAndEmojiDoNotUseRawSourceOffsets() {
        assertParagraph("# Sehr **langer** Titel 😀\n\n[Link](https://example.org/a/long/path)\n\n## Ziel\n\nText", "Ziel", "Ziel");
    }
    @Test void duplicatedParagraphsNavigateToTheCorrectOccurrence() {
        String source = "# Titel\n\nGleich\n\nGleich\n\nGleich";
        int position = IssueNavigation.exportedParagraph(source, source.lastIndexOf("Gleich"), null);
        assertEquals(new MarkdownParser().parseInto(source).getText().lastIndexOf("Gleich"), position);
    }
    @Test void fencedCodeAndTableCellsMapToContainingParagraph() {
        assertParagraph("# Titel\n\n```java\nfirst\nsecond\n```\n\nEnde", "second", "second");
        assertParagraph("Vorher\n\n| A | B |\n| --- | --- |\n| 1 | 2 |\n\nEnde", "2 |", "A");
    }
    @Test void imagesAndSetextHeadingsRemainStable() {
        assertParagraph("![Bild](https://example.org/picture.png)\n\nTitel\n=====\n\nZiel", "Ziel", "Ziel");
        assertEquals(0, IssueNavigation.exportedParagraph("# Titel", 2, null));
        assertThrows(IllegalArgumentException.class, () -> IssueNavigation.exportedParagraph("Text", 9, null));
    }
}
