package de.richmarkdown.features.editing;

import com.gluonhq.richtextarea.model.*;
import javafx.scene.text.FontWeight;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DocumentEditsTest {
    private final ParagraphDecoration paragraph = ParagraphDecoration.builder().presets().build();
    private final TextDecoration bold = TextDecoration.builder().presets().fontWeight(FontWeight.BOLD).url("https://example.org").build();
    private Document styled(String text) { return new Document(text, List.of(new DecorationModel(0, text.length(), bold, paragraph)), 0); }
    @Test void expandsUnicodeWithoutDroppingStyles() {
        var original = styled("Straße 😀");
        var result = DocumentEdits.transform(original, 0, original.getText().length(), DocumentEdits.Transform.UPPER);
        assertEquals("STRASSE 😀", result.getText());
        assertEquals(bold, result.getDecorations().getFirst().getDecoration());
        assertEquals(result.getText().length(), result.getDecorations().getFirst().getLength());
        assertEquals("Straße 😀", original.getText());
    }
    @Test void lowerAndSentenceHandleUnicodeAndPunctuation() {
        assertEquals("äö", DocumentEdits.transform(styled("ÄÖ"), 0, 2, DocumentEdits.Transform.LOWER).getText());
        String sentence = "HALLO WELT! NÄCHSTER SATZ? JA.";
        assertEquals("Hallo welt! Nächster satz? Ja.", DocumentEdits.transform(styled(sentence), 0, sentence.length(), DocumentEdits.Transform.SENTENCE).getText());
    }
    @Test void replacePreservesUnselectedDecorationsAndRejectsSurrogateCuts() {
        var source = styled("A😀Z");
        var result = DocumentEdits.replace(source, 1, 3, new Document("B"));
        assertEquals("ABZ", result.getText());
        assertEquals(bold, result.getDecorations().getLast().getDecoration());
        assertThrows(IllegalArgumentException.class, () -> DocumentEdits.replace(source, 2, 3, new Document("")));
        assertThrows(IndexOutOfBoundsException.class, () -> DocumentEdits.slice(source, 0, 99));
    }
    @Test void clearsOnlyRequestedTextFormatting() {
        var source = styled("linked");
        var result = DocumentEdits.clear(source, 0, 6, true, false);
        var decoration = (TextDecoration) result.getDecorations().getFirst().getDecoration();
        assertEquals("", decoration.getURL());
        assertEquals(FontWeight.BOLD, decoration.getFontWeight());
        var plain = DocumentEdits.clear(source, 0, 6, false, true);
        assertEquals(FontWeight.NORMAL, ((TextDecoration) plain.getDecorations().getFirst().getDecoration()).getFontWeight());
        assertEquals("linked", plain.getText());
    }
    @Test void sortAndUniquePreserveStyleAndFinalNewline() {
        assertEquals("a\nb\n", DocumentEdits.transform(styled("b\na\n"), 0, 4, DocumentEdits.Transform.SORT_LINES).getText());
        assertEquals("b\na", DocumentEdits.transform(styled("b\na\nb"), 0, 5, DocumentEdits.Transform.UNIQUE_LINES).getText());
    }
    @Test void moveParagraphHandlesLastLineWithoutNewline() {
        assertEquals("b\na\nc", DocumentEdits.moveParagraph(styled("a\nb\nc"), 2, -1).getText());
        assertEquals("a\nc\nb", DocumentEdits.moveParagraph(styled("a\nb\nc"), 2, 1).getText());
        assertEquals("a\nb\nc", DocumentEdits.moveParagraph(styled("a\nb\nc"), 0, -1).getText());
        assertEquals("a\nb\nc", DocumentEdits.moveParagraph(styled("a\nb\nc"), 4, 1).getText());
        assertEquals("\na", DocumentEdits.moveParagraph(styled("a\n"), 2, -1).getText());
        assertEquals("b\na\n", DocumentEdits.moveParagraph(styled("a\nb\n"), 0, 1).getText());
    }
    @Test void paragraphRangeAndDuplication() {
        assertEquals(new DocumentEdits.Range(2, 4), DocumentEdits.paragraph("a\nb\nc", 2));
        assertEquals("a\nb\nb\nc", DocumentEdits.duplicateParagraph(styled("a\nb\nc"), 2).getText());
        assertEquals("a\nb\nc\nc", DocumentEdits.duplicateParagraph(styled("a\nb\nc"), 4).getText());
        assertEquals(new DocumentEdits.Range(0, 0), DocumentEdits.paragraph("", 0));
    }
    @Test void moveRangeBothDirectionsPreservesNoFinalNewline() {
        var source = styled("A\na\nB\nb");
        assertEquals("B\nb\nA\na", DocumentEdits.moveRange(source, 4, 7, 0).getText());
        assertEquals("B\nb\nA\na", DocumentEdits.moveRange(source, 0, 4, 7).getText());
        assertSame(source, DocumentEdits.moveRange(source, 0, 4, 2));
    }
    @Test void replacementsPreservePageSettings() {
        var source = styled("original");
        var result = DocumentEdits.replace(source, 0, 8, new Document("new"));
        assertEquals(source.getPageFormat(), result.getPageFormat());
        assertEquals(source.getHeadersFooters(), result.getHeadersFooters());
    }
    @Test void imageAndTableMetadataSurviveTextEdits() {
        ImageDecoration image = new ImageDecoration("file:/tmp/CaseSensitive.png");
        var table = ParagraphDecoration.builder().presets().tableDecoration(new TableDecoration(1, 2)).build();
        var source = new Document("image\ncell", List.of(new DecorationModel(0, 5, image, paragraph),
                new DecorationModel(5, 5, bold, table)), 0);
        var result = DocumentEdits.transform(source, 0, 10, DocumentEdits.Transform.UPPER);
        assertEquals("image\nCELL", result.getText());
        assertEquals(image, result.getDecorations().getFirst().getDecoration());
        assertTrue(result.getDecorations().getLast().getParagraphDecoration().hasTableDecoration());
        var cleared = DocumentEdits.clear(source, 0, 10, false, false);
        assertEquals(image, cleared.getDecorations().getFirst().getDecoration());
        assertThrows(IllegalArgumentException.class, () -> DocumentEdits.clear(source, 0, 10, false, true));
    }
}
