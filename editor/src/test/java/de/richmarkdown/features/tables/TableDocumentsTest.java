package de.richmarkdown.features.tables;

import com.gluonhq.richtextarea.model.*;
import de.richmarkdown.markdown.MarkdownParser;
import de.richmarkdown.markdown.MarkdownWriter;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TableDocumentsTest {
    private final MarkdownParser parser = new MarkdownParser();
    private final MarkdownWriter writer = new MarkdownWriter();
    @Test void findsTablesAndPreservesInlineDecorationsWhenSorting() {
        Document document = parser.parseInto("# Titel\n\n| Name | Wert |\n| --- | --- |\n| **Beta** | 10 |\n| *Alpha* | 2 |\n\nEnde");
        int start = document.getText().indexOf("Name"); TableDocuments.Located table = TableDocuments.at(document, start + 2).orElseThrow();
        Document changed = TableDocuments.replace(document, table, List.of(table.data().sort(0, false, false)));
        String markdown = writer.write(changed); assertTrue(markdown.contains("**Beta**")); assertTrue(markdown.contains("*Alpha*")); assertTrue(markdown.indexOf("*Alpha*") < markdown.indexOf("**Beta**")); assertTrue(markdown.contains("# Titel")); assertTrue(markdown.contains("Ende")); assertFalse(TableDocuments.at(document, 0).isPresent());
    }
    @Test void documentTransformationsPreserveAlignmentAndImagesOutsideTable() {
        Document document = parser.parseInto("Vorher\n\n| A | B |\n| :--- | ---: |\n| 1 | 2 |\n\nNachher"); int start = document.getText().indexOf("A\u200b"); TableDocuments.Located table = TableDocuments.at(document, start).orElseThrow();
        Document changed = TableDocuments.replace(document, table, List.of(table.data().transpose().transpose())); assertEquals(writer.write(document), writer.write(changed));
        assertTrue(writer.write(changed).contains("| --- | ---: |"));
        assertEquals(javafx.scene.text.TextAlignment.RIGHT, TableDocuments.at(parser.parseInto(writer.write(changed)), start).orElseThrow().data().rows().getFirst().get(1).alignment());
    }
    @Test void centeredAlignmentSurvivesSortingAndSaving() {
        Document document = parser.parseInto("| A | B |\n| :---: | ---: |\n| 2 | two |\n| 1 | one |");
        TableDocuments.Located table = TableDocuments.at(document, 0).orElseThrow();
        String markdown = writer.write(TableDocuments.replace(document, table, List.of(table.data().sort(0, true, false))));
        assertTrue(markdown.contains("| :---: | ---: |"));
        assertEquals(javafx.scene.text.TextAlignment.CENTER, TableDocuments.at(parser.parseInto(markdown), 0).orElseThrow().data().rows().get(1).getFirst().alignment());
    }
    @Test void insertAtMiddleAddsParagraphBoundariesAndPreservesText() {
        Document source = parser.parseInto("**VorherNachher**"); TableData data = TableData.plain(List.of(List.of("A", "B"), List.of("1", "2")));
        Document changed = TableDocuments.insert(source, 6, data); String markdown = writer.write(changed); assertTrue(markdown.contains("Vorher")); assertTrue(markdown.contains("Nachher")); assertTrue(markdown.contains("| A | B |")); assertThrows(IllegalArgumentException.class, () -> TableDocuments.insert(changed, changed.getText().indexOf("A\u200b"), data));
    }
    @Test void splitAndJoinUseOnlyNormalMarkdown() {
        Document source = parser.parseInto("| A |\n| --- |\n| 1 |\n| 2 |\n"); TableDocuments.Located located = TableDocuments.at(source, 0).orElseThrow(); Document split = TableDocuments.replace(source, located, located.data().split(2)); String markdown = writer.write(split); assertEquals(2, markdown.lines().filter(line -> line.contains("---")).count());
    }
    @Test void rejectsMultilineCellsRatherThanInventingSyntax() { assertThrows(IllegalArgumentException.class, () -> TableDocuments.tables(List.of(TableData.plain(List.of(List.of("a\nb")))))); }
    @Test void preservesSurroundingImageDecorationsWithoutSerialization() {
        ImageDecoration image = new ImageDecoration("https://example.org/image.png"); ParagraphDecoration pd = ParagraphDecoration.builder().presets().build();
        Document source = new Document("img\ntext", List.of(new DecorationModel(0, 3, image, pd), new DecorationModel(3, 5, TextDecoration.builder().presets().build(), pd)), 0);
        Document changed = TableDocuments.insert(source, 4, TableData.plain(List.of(List.of("Header"), List.of("value")))); assertSame(image, changed.getDecorations().getFirst().getDecoration()); assertTrue(changed.getText().endsWith("text"));
    }
}
