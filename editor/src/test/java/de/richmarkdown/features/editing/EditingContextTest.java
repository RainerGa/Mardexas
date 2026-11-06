package de.richmarkdown.features.editing;

import com.gluonhq.richtextarea.model.*;
import de.richmarkdown.markdown.MarkdownParser;
import de.richmarkdown.markdown.MarkdownWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EditingContextTest {
    private Document parse(String markdown) { return new MarkdownParser().parseInto(markdown); }
    private String markdown(Document document) { return new MarkdownWriter().write(document); }

    @ParameterizedTest
    @ValueSource(strings = {"- List item", "1. Numbered item", "# Heading", "> Quote", "```java\ncode\n```", "| A | B |\n|---|---|\n| 1 | 2 |"})
    void structuralBlocksCannotBeMovedOrDuplicatedAsOrdinaryParagraphs(String markdown) {
        var source = parse(markdown + "\n\nTail");
        assertThrows(IllegalArgumentException.class, () -> DocumentEdits.moveParagraph(source, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> DocumentEdits.duplicateParagraph(source, 0));
    }

    @Test void movingProseCannotSplitAdjacentStructuralBlock() {
        var source = parse("- First\n- Second");
        var plain = new Document("Before\n");
        var combined = DocumentEdits.join(plain, List.of(plain, source), 0);
        assertThrows(IllegalArgumentException.class, () -> DocumentEdits.moveParagraph(combined, 0, 1));
    }

    @Test void imageBlocksCannotBeMovedByParagraphCommands() {
        var source = new Document("image\nText", List.of(
                new DecorationModel(0, 6, new ImageDecoration("file:/tmp/picture.png"), ParagraphDecoration.builder().presets().build()),
                new DecorationModel(6, 4, TextDecoration.builder().presets().build(), ParagraphDecoration.builder().presets().build())), 0);
        assertThrows(IllegalArgumentException.class, () -> DocumentEdits.moveParagraph(source, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> DocumentEdits.duplicateParagraph(source, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"- b\n- a", "# b\n\n# a", "| b | a |\n|---|---|", "> b\n> a"})
    void sortingNeverRearrangesStructuredBlocks(String markdown) {
        var source = parse(markdown);
        assertThrows(IllegalArgumentException.class, () -> DocumentEdits.transform(source, 0,
                source.getText().length(), DocumentEdits.Transform.SORT_LINES));
    }

    @Test void sortingCannotStitchPartialLinesTogether() {
        var source = new Document("before b\na after");
        assertThrows(IllegalArgumentException.class, () -> DocumentEdits.transform(source, 7, 10, DocumentEdits.Transform.SORT_LINES));
        assertThrows(IllegalArgumentException.class, () -> DocumentEdits.transform(source, 0, 10, DocumentEdits.Transform.SORT_LINES));
    }

    @Test void sortingInsideOneCodeBlockKeepsItsLanguage() {
        var source = parse("```java\nz\na\n```");
        var result = DocumentEdits.transform(source, 0, source.getText().length(), DocumentEdits.Transform.SORT_LINES);
        assertTrue(result.getText().startsWith("a\nz"));
        assertTrue(markdown(result).startsWith("```java\n"));
    }

    @Test void sortingCannotMixCodeWithProse() {
        var source = parse("```java\nz\na\n```\n\nProse");
        assertThrows(IllegalArgumentException.class, () -> DocumentEdits.transform(source, 0,
                source.getText().length(), DocumentEdits.Transform.SORT_LINES));
    }

    @Test void clearingInlineFormattingRetainsHeadingTypeAndCodeLanguage() {
        var heading = parse("# Heading *emphasis*");
        assertTrue(markdown(DocumentEdits.clear(heading, 0, heading.getText().length(), false, false)).startsWith("# Heading emphasis"));
        var code = parse("```java\nString VALUE;\n```");
        assertEquals(markdown(code), markdown(DocumentEdits.clear(code, 0, code.getText().length(), false, false)));
        assertFalse(markdown(DocumentEdits.clear(code, 0, code.getText().length(), false, true)).contains("```"));
    }

    @Test void ordinaryParagraphCommandsTreatEmptyDocumentAndBoundariesAsNoOps() {
        var empty = new Document("");
        assertSame(empty, DocumentEdits.duplicateParagraph(empty, 0));
        assertSame(empty, DocumentEdits.moveParagraph(empty, 0, 1));
        var source = new Document("First\nLast");
        assertSame(source, DocumentEdits.moveParagraph(source, 0, -1));
        assertSame(source, DocumentEdits.moveParagraph(source, source.getText().length(), 1));
        var trailing = new Document("First\n");
        assertSame(trailing, DocumentEdits.moveParagraph(trailing, trailing.getText().length(), 1));
        assertThrows(IllegalArgumentException.class, () -> DocumentEdits.moveParagraph(source, 0, 0));
    }

    @Test void chapterMovedPastUnterminatedLastChapterPointsAtItsOwnStart() {
        var source = new Document("A\na\nB\nb");
        var result = DocumentEdits.moveRange(source, 0, 4, source.getText().length());
        assertEquals("B\nb\nA\na", result.getText());
        assertEquals(result.getText().indexOf('A'), result.getCaretPosition());
    }
}
