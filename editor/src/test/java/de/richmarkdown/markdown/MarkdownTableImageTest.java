package de.richmarkdown.markdown;

import com.gluonhq.richtextarea.model.DecorationModel;
import com.gluonhq.richtextarea.model.Document;
import com.gluonhq.richtextarea.model.ImageDecoration;
import com.gluonhq.richtextarea.model.ParagraphDecoration;
import com.gluonhq.richtextarea.model.TableDecoration;
import com.gluonhq.richtextarea.model.TextDecoration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD Phase 6: Tabellen (GFM) und Bilder – Writer, Parser, Round-Trip
 * (PROJEKTPLAN 5.3, 7.1).
 */
class MarkdownTableImageTest {

    private final MarkdownWriter writer = new MarkdownWriter();
    private final MarkdownParser parser = new MarkdownParser();

    private static final String SEP = "\u200b";
    private static final String IMAGE_PLACEHOLDER = "\ufeff";

    // ---------- Helfer ----------

    private Document doc(String text, DecorationModel... models) {
        return new Document(text, java.util.List.of(models), 0);
    }

    private DecorationModel dm(int start, int length, com.gluonhq.richtextarea.model.Decoration td,
                               ParagraphDecoration pd) {
        return new DecorationModel(start, length, td, pd);
    }

    private TextDecoration normal() {
        return TextDecoration.builder().presets().build();
    }

    private ParagraphDecoration paragraph() {
        return ParagraphDecoration.builder().presets().build();
    }

    private ParagraphDecoration tableDecoration(int rows, int columns) {
        return ParagraphDecoration.builder().presets()
                .tableDecoration(new TableDecoration(rows, columns))
                .build();
    }

    // ---------- Writer: Tabellen ----------

    @Test
    void writesGfmTable() {
        Document document = doc("a" + SEP + "b" + SEP + "c" + SEP + "d",
                dm(0, 7, normal(), tableDecoration(2, 2)));
        assertEquals("| a | b |\n| --- | --- |\n| c | d |", writer.write(document));
    }

    @Test
    void writesSingleRowTable() {
        Document document = doc("h1" + SEP + "h2",
                dm(0, 5, normal(), tableDecoration(1, 2)));
        assertEquals("| h1 | h2 |\n| --- | --- |", writer.write(document));
    }

    @Test
    void writesTableWithEmptyCells() {
        Document document = doc("a" + SEP + SEP + "c" + SEP + "d",
                dm(0, 6, normal(), tableDecoration(2, 2)));
        assertEquals("| a |  |\n| --- | --- |\n| c | d |", writer.write(document));
    }

    @Test
    void writesTableWithFormattedCells() {
        Document document = doc("**a**" + SEP + "b" + SEP + "c" + SEP + "d",
                dm(0, 11, normal(), tableDecoration(2, 2)));
        assertEquals("| **a** | b |\n| --- | --- |\n| c | d |", writer.write(document));
    }

    @Test
    void tableIsSeparatedFromOtherBlocks() {
        Document document = doc("text\n\n" + "a" + SEP + "b",
                dm(0, 5, normal(), paragraph()),
                dm(5, 1, normal(), paragraph()),
                dm(6, 3, normal(), tableDecoration(1, 2)));
        assertEquals("text\n\n| a | b |\n| --- | --- |", writer.write(document));
    }

    @Test
    void tableWithTrailingEmptyParagraphIsWrittenOnce() {
        // So stellt die Bibliothek eine native Tabelle dar: EIN Absatz mit
        // Zellseparatoren, gefolgt von einem leeren Absatz (Ende des
        // Dokuments). Der leere Folgeabsatz darf nicht über den
        // Decoration-Fallback zu einer zweiten Tabelle werden.
        Document document = doc("a" + SEP + "b" + SEP + "c" + SEP + "d" + "\n",
                dm(0, 8, normal(), tableDecoration(2, 2)));
        assertEquals("| a | b |\n| --- | --- |\n| c | d |\n", writer.write(document));
    }

    @Test
    void tableWithEmptyTrailingParagraphAfterTextIsWrittenOnce() {
        Document document = doc("before\n" + SEP + SEP + SEP + "\n",
                dm(0, 6, normal(), paragraph()),
                dm(7, 4, normal(), tableDecoration(2, 2)));
        assertEquals("before\n\n|  |  |\n| --- | --- |\n|  |  |\n", writer.write(document));
    }

    // ---------- Writer: Bilder ----------

    @Test
    void writesImage() {
        Document document = doc(IMAGE_PLACEHOLDER,
                dm(0, 1, new ImageDecoration("https://example.com/img.png"), paragraph()));
        assertEquals("![](https://example.com/img.png)", writer.write(document));
    }

    @Test
    void writesImageWithLocalPath() {
        Document document = doc(IMAGE_PLACEHOLDER,
                dm(0, 1, new ImageDecoration("images/pic.png"), paragraph()));
        assertEquals("![](images/pic.png)", writer.write(document));
    }

    @Test
    void imageIsSeparatedFromOtherBlocks() {
        Document document = doc("text\n\n" + IMAGE_PLACEHOLDER,
                dm(0, 5, normal(), paragraph()),
                dm(5, 1, normal(), paragraph()),
                dm(6, 1, new ImageDecoration("img.png"), paragraph()));
        assertEquals("text\n\n![](img.png)", writer.write(document));
    }

    // ---------- Parser: Tabellen ----------

    @Test
    void parsesGfmTable() {
        MarkdownModel model = parser.parse("| a | b |\n| --- | --- |\n| 1 | 2 |");
        assertEquals(1, model.getBlocks().size());
        assertTrue(model.getBlocks().get(0) instanceof TableBlock);
        TableBlock table = (TableBlock) model.getBlocks().get(0);
        assertEquals(2, table.getRows());
        assertEquals(2, table.getColumns());
        assertEquals("a", table.getCellText(0, 0));
        assertEquals("b", table.getCellText(0, 1));
        assertEquals("1", table.getCellText(1, 0));
        assertEquals("2", table.getCellText(1, 1));
    }

    @Test
    void parsesTableWithoutLeadingPipes() {
        MarkdownModel model = parser.parse("a | b\n--- | ---\n1 | 2");
        assertTrue(model.getBlocks().get(0) instanceof TableBlock);
        assertEquals("a", ((TableBlock) model.getBlocks().get(0)).getCellText(0, 0));
    }

    @Test
    void parsesTableWithAlignment() {
        MarkdownModel model = parser.parse("| a | b |\n| :--- | ---: |\n| 1 | 2 |");
        TableBlock table = (TableBlock) model.getBlocks().get(0);
        assertEquals("a", table.getCellText(0, 0));
        assertEquals(2, table.getColumns());
    }

    @Test
    void parsesTableWithFormattedCells() {
        MarkdownModel model = parser.parse("| **a** | b |\n| --- | --- |\n| 1 | 2 |");
        TableBlock table = (TableBlock) model.getBlocks().get(0);
        // Zellinhalt wird als Inline-Läufe geparst (Formatierung erhalten)
        assertEquals("a", table.getCellText(0, 0));
        assertTrue(table.getCell(0, 0).get(0).isBold());
        assertFalse(table.getCell(0, 1).get(0).isBold());
    }

    @Test
    void parseInto_createsTableDecoration() {
        Document document = parser.parseInto("| a | b |\n| --- | --- |\n| 1 | 2 |");
        ParagraphDecoration pd = document.getDecorations().get(0).getParagraphDecoration();
        assertNotNull(pd.getTableDecoration());
        assertEquals(2, pd.getTableDecoration().getRows());
        assertEquals(2, pd.getTableDecoration().getColumns());
    }

    @Test
    void parseInto_tableTextContainsSeparators() {
        Document document = parser.parseInto("| a | b |\n| --- | --- |\n| 1 | 2 |");
        assertTrue(document.getText().contains(SEP), "table text must contain separators: " + document);
    }

    // ---------- Parser: Bilder ----------

    @Test
    void parsesImage() {
        MarkdownModel model = parser.parse("![alt](https://example.com/img.png)");
        assertTrue(model.getBlocks().get(0) instanceof ImageBlock);
        ImageBlock image = (ImageBlock) model.getBlocks().get(0);
        assertEquals("https://example.com/img.png", image.getUrl());
    }

    @Test
    void parsesImageWithLocalPath() {
        MarkdownModel model = parser.parse("![](images/pic.png)");
        assertEquals("images/pic.png", ((ImageBlock) model.getBlocks().get(0)).getUrl());
    }

    @Test
    void parseInto_createsImageDecoration() {
        Document document = parser.parseInto("![alt](img.png)");
        boolean found = document.getDecorations().stream()
                .anyMatch(dm -> dm.getDecoration() instanceof ImageDecoration);
        assertTrue(found, "image decoration expected in " + document);
    }

    @Test
    void parseInto_imageTextContainsPlaceholder() {
        Document document = parser.parseInto("![alt](img.png)");
        assertTrue(document.getText().contains(IMAGE_PLACEHOLDER),
                "image placeholder expected in " + document);
    }

    // ---------- Round-Trip ----------

    @Test
    void roundTrip_table() {
        assertRoundTrip("| a | b |\n| --- | --- |\n| 1 | 2 |");
    }

    @Test
    void roundTrip_tableWithEmptyCells() {
        assertRoundTrip("| a |  |\n| --- | --- |\n| c | d |");
    }

    @Test
    void roundTrip_tableWithFormattedCells() {
        assertRoundTrip("| **a** | b |\n| --- | --- |\n| 1 | 2 |");
    }

    @Test
    void roundTrip_image() {
        assertRoundTrip("![](https://example.com/img.png)");
    }

    @Test
    void roundTrip_imageWithLocalPath() {
        assertRoundTrip("![](images/pic.png)");
    }

    @Test
    void roundTrip_tableAmongOtherBlocks() {
        assertRoundTrip("text\n\n| a | b |\n| --- | --- |\n| 1 | 2 |\n\nmore text");
    }

    @Test
    void roundTrip_imageAmongOtherBlocks() {
        assertRoundTrip("text\n\n![](img.png)\n\nmore text");
    }

    @Test
    void roundTrip_documentWithTableAndImage() {
        assertRoundTrip("# Title\n\n| a | b |\n| --- | --- |\n| 1 | 2 |\n\n![](img.png)\n\n- x\n- y");
    }

    // ---------- Helfer ----------

    private void assertRoundTrip(String markdown) {
        Document document = parser.parseInto(markdown);
        String written = writer.write(document);
        assertEquals(markdown, written, "round trip failed");
    }
}
