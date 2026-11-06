package de.richmarkdown.markdown;

import com.gluonhq.richtextarea.model.TextDecoration;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CodeEditingTest {
    @Test void fencesPreserveLanguageAndEmbeddedDelimiters() {
        String code = "String x = \"```\";\n~~~\n```not a closing fence\n";
        String markdown = CodeEditing.fenced(code, "java");
        assertTrue(markdown.startsWith("````java\n"));
        CodeBlockBlock block = (CodeBlockBlock) new MarkdownParser().parse(markdown).getBlocks().getFirst();
        assertEquals("java", block.getLanguage());
        assertEquals(List.of(code.split("\n", -1)), block.getLines());
        assertTrue(CodeEditing.fenced("body", "info`text").startsWith("~~~info`text"));
    }
    @Test void languageSurvivesDocumentRoundTrip() {
        MarkdownParser parser = new MarkdownParser();
        String markdown = "```java\npublic class A {\n    // x\n}\n```\n\n```python\ndef f():\n    pass\n```\n";
        String roundTrip = new MarkdownWriter().write(parser.parseInto(markdown));
        assertTrue(roundTrip.contains("```java\n"));
        assertTrue(roundTrip.contains("```python\n"));
        assertEquals(roundTrip, new MarkdownWriter().write(parser.parseInto(roundTrip)));
    }
    @Test void metadataNormalizesCopiesAndParticipatesInEquality() {
        TextDecoration code = TextDecoration.builder().presets().codeLanguage("java").build();
        assertEquals("java", TextDecoration.builder().fromDecoration(code).build().getCodeLanguage());
        assertEquals("java", TextDecoration.builder().fontSize(15).build().normalize(code).getCodeLanguage());
        assertNotEquals(code, TextDecoration.builder().presets().codeLanguage("python").build());
        assertNotEquals(code.hashCode(), TextDecoration.builder().presets().codeLanguage("python").build().hashCode());
        assertThrows(IllegalArgumentException.class, () -> TextDecoration.builder().codeLanguage("bad\nlang"));
        assertEquals("", TextDecoration.builder().presets().build().getCodeLanguage());
    }
    @Test void indentationPreservesBlankLinesAndCanBeUndone() {
        String original = "first\n  second\n\nthird\n";
        assertEquals(original, CodeEditing.indent(CodeEditing.indent(original, 4, false), 4, true));
        assertEquals("a\nb", CodeEditing.indent("\ta\n  b", 4, true));
    }
    @Test void tabConversionUsesTabStopsAndDoesNotChangeInteriorSpaces() {
        assertEquals("a   b\n    x", CodeEditing.expandTabs("a\tb\n\tx", 4));
        assertEquals("\t x  y\n\t\tz", CodeEditing.leadingSpacesToTabs("     x  y\n        z", 4));
    }
    @Test void syntaxPreviewIsImmutableAndNeverModifiesCode() {
        String code = "public String x = \"hello\"; // comment\nreturn 42;";
        var spans = CodeEditing.highlight(code, "java");
        assertEquals(code, spans.stream().map(CodeEditing.Span::text).collect(java.util.stream.Collectors.joining()));
        for (String kind : List.of("keyword", "string", "comment", "number")) {
            assertTrue(spans.stream().anyMatch(span -> span.kind().equals(kind)), kind);
        }
        assertThrows(UnsupportedOperationException.class, spans::clear);
        String large = "x".repeat(CodeEditing.MAX_HIGHLIGHT_CHARS + 1);
        assertEquals(List.of(new CodeEditing.Span(large, "plain")), CodeEditing.highlight(large, "java"));
    }
    @Test void inputsAndOutputResourcesAreBounded() {
        assertThrows(IllegalArgumentException.class, () -> CodeEditing.indent("a", 0, false));
        assertThrows(IllegalArgumentException.class, () -> CodeEditing.expandTabs("a", 17));
        assertThrows(IllegalArgumentException.class, () -> CodeEditing.fenced("a", "bad\nlang"));
        assertThrows(IllegalArgumentException.class, () -> CodeEditing.highlight("x".repeat(CodeEditing.MAX_CODE_CHARS + 1), "java"));
    }
    @Test void singleColumnTablesRemainTablesAndSetextRemainsHeading() {
        MarkdownParser parser = new MarkdownParser();
        assertInstanceOf(TableBlock.class, parser.parse("| A |\n| --- |\n| B |\n").getBlocks().getFirst());
        String table = new MarkdownWriter().write(parser.parseInto("| A |\n| --- |\n| B |\n"));
        assertInstanceOf(TableBlock.class, parser.parse(table).getBlocks().getFirst());
        assertInstanceOf(HeadingBlock.class, parser.parse("Title\n---\n").getBlocks().getFirst());
    }
}
