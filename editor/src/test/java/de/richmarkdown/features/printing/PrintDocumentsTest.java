package de.richmarkdown.features.printing;

import com.gluonhq.richtextarea.model.*;
import de.richmarkdown.markdown.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PrintDocumentsTest {
    private final MarkdownParser parser = new MarkdownParser();
    @Test void chapterContainsSubchaptersStopsAtSiblingAndDoesNotMutateOriginal() { Document source = parser.parseInto("Vorspann\n\n# Eins\n\nText\n\n## Unter\n\nInhalt\n\n# Zwei\n\nEnde"); String original = source.getText(); Document chapter = PrintDocuments.chapter(source, source.getText().indexOf("Text")); assertTrue(chapter.getText().contains("Unter")); assertFalse(chapter.getText().contains("Zwei")); assertFalse(chapter.getText().contains("Vorspann")); assertEquals(original, source.getText()); assertThrows(IllegalArgumentException.class, () -> PrintDocuments.chapter(source, 0)); }
    @Test void nestedChapterPrintsOnlyItsOwnScope() { Document source = parser.parseInto("# Eins\n\n## A\n\naaa\n\n### Kind\n\nk\n\n## B\n\nbbb"); Document chapter = PrintDocuments.chapter(source, source.getText().indexOf("aaa")); assertTrue(chapter.getText().contains("Kind")); assertFalse(chapter.getText().contains("bbb")); }
    @Test void selectionRetainsStylesAndExpandsPartialTable() { Document source = parser.parseInto("**Vorher**\n\n| A | B |\n| --- | --- |\n| 1 | 2 |\n\nEnde"); Document selection = PrintDocuments.selection(source, 0, 3); assertEquals("Vor", selection.getText()); assertTrue(((TextDecoration) selection.getDecorations().getFirst().getDecoration()).getFontWeight() == javafx.scene.text.FontWeight.BOLD); int a = source.getText().indexOf("A\u200b"); Document table = PrintDocuments.selection(source, a + 1, a + 2); assertTrue(table.getText().startsWith("A")); assertTrue(table.getText().contains("2")); assertThrows(IllegalArgumentException.class, () -> PrintDocuments.selection(source, 0, 0)); }
    @Test void printFontsScaleHeadingsAndRetainCodeFamilyWithoutChangingSource() { Document source = parser.parseInto("# Titel\n\nText\n\n```\ncode\n```\n"); PrintOptions options = new PrintOptions(20,20,20,20,"Serif",28,"","",false,PrintOptions.Scope.DOCUMENT,""); Document prepared = PrintDocuments.prepare(source, options, 0, 0, 0); TextDecoration heading = (TextDecoration) prepared.getDecorations().getFirst().getDecoration(); assertEquals(MarkdownStyles.headingSize(1) * 2, heading.getFontSize()); assertEquals("Serif", heading.getFontFamily()); assertTrue(prepared.getDecorations().stream().anyMatch(dm -> dm.getDecoration() instanceof TextDecoration td && MarkdownStyles.MONOSPACED.equals(td.getFontFamily()))); assertEquals(MarkdownStyles.headingSize(1), ((TextDecoration) source.getDecorations().getFirst().getDecoration()).getFontSize()); }
}
