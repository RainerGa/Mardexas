package de.richmarkdown.markdown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD: MarkdownStyles ist die Single Source of Truth für das kanonische
 * Style-Mapping (PROJEKTPLAN 5.2/5.3).
 */
class MarkdownStylesTest {

    @Test
    void headingSizesFollowCanonicalMapping() {
        assertEquals(32d, MarkdownStyles.headingSize(1));
        assertEquals(24d, MarkdownStyles.headingSize(2));
        assertEquals(19d, MarkdownStyles.headingSize(3));
        assertEquals(16d, MarkdownStyles.headingSize(4));
        assertEquals(15d, MarkdownStyles.headingSize(5));
        assertEquals(13d, MarkdownStyles.headingSize(6));
    }

    @Test
    void headingSizeRejectsInvalidLevels() {
        assertEquals(-1, MarkdownStyles.headingLevel(0));
        assertEquals(-1, MarkdownStyles.headingLevel(7));
    }

    @Test
    void headingLevelIdentifiesHeadingSizes() {
        assertEquals(1, MarkdownStyles.headingLevel(32d));
        assertEquals(2, MarkdownStyles.headingLevel(24d));
        assertEquals(3, MarkdownStyles.headingLevel(19d));
        assertEquals(4, MarkdownStyles.headingLevel(16d));
        assertEquals(5, MarkdownStyles.headingLevel(15d));
        assertEquals(6, MarkdownStyles.headingLevel(13d));
    }

    @Test
    void defaultFontSizeIsNotAHeadingSize() {
        // Kritisch: Body-Text (14px, Default) darf nie als Heading erkannt werden,
        // sonst würde **fett** im Writer zu einer Überschrift (Round-Trip-Bruch).
        assertFalse(MarkdownStyles.isHeadingSize(MarkdownStyles.DEFAULT_FONT_SIZE));
        assertEquals(-1, MarkdownStyles.headingLevel(MarkdownStyles.DEFAULT_FONT_SIZE));
    }

    @Test
    void arbitrarySizesAreNotHeadings() {
        assertFalse(MarkdownStyles.isHeadingSize(12d));
        assertFalse(MarkdownStyles.isHeadingSize(18d));
        assertFalse(MarkdownStyles.isHeadingSize(20d));
    }

    @Test
    void codeBlockBackgroundIsDefinedAndNotTransparent() {
        assertTrue(MarkdownStyles.isCodeBlockBackground(MarkdownStyles.CODE_BLOCK_BACKGROUND));
        assertFalse(MarkdownStyles.isCodeBlockBackground("transparent"));
        assertFalse(MarkdownStyles.isCodeBlockBackground(null));
    }
}
