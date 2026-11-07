package de.richmarkdown.performance;

import de.richmarkdown.io.MarkdownFileHandler;
import de.richmarkdown.markdown.MarkdownParser;
import de.richmarkdown.markdown.MarkdownWriter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/** Comparative integration benchmark using the project's JUnit runner, not a JMH microbenchmark. */
@Tag("performance")
class MarkdownPipelinePerformanceTest {
    private static volatile Object consumed;

    @ParameterizedTest
    @ValueSource(strings = {"long-inline", "unclosed-delimiters", "table", "code"})
    void specialTokenShapes(String shape) throws Exception {
        String source = switch (shape) {
            case "long-inline" -> "Text **fett** und *kursiv* mit `code`. ".repeat(1000);
            case "unclosed-delimiters" -> "[offen <tag * _ ` ".repeat(1000);
            case "table" -> "| A | B | C |\n| --- | --- | --- |\n" + "| **eins** | zwei | `drei` |\n".repeat(500);
            case "code" -> "```java\n" + "int answer = 42;\n".repeat(1500) + "```";
            default -> throw new IllegalArgumentException(shape);
        };
        var parser = new MarkdownParser();
        var document = parser.parseInto(source);
        assertFalse(document.getText().isBlank(), "The token workload must produce document content");
        var report = new PerformanceReport("tokens-" + shape);
        report.metric("parse_model", "ms", measure(() -> parser.parse(source)));
        report.metric("parse_document", "ms", measure(() -> parser.parseInto(source)));
        report.metric("write_document", "ms", measure(() -> new MarkdownWriter().write(document)));
        report.write(source.length(), document.getText().split("\n", -1).length, document.getDecorations().size());
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 1000, 3000})
    void parsingSerializationAndIo(int paragraphs, @TempDir Path directory) throws Exception {
        String source = PerformanceDocuments.markdown(paragraphs);
        var parser = new MarkdownParser();
        var writer = new MarkdownWriter();
        var document = parser.parseInto(source);
        assertTrue(document.getDecorations().size() > paragraphs);
        var report = new PerformanceReport("pipeline-" + paragraphs);
        report.metric("parse_model", "ms", measure(() -> parser.parse(source)));
        report.metric("parse_document", "ms", measure(() -> parser.parseInto(source)));
        report.metric("write_document", "ms", measure(() -> writer.write(document)));
        String canonical = writer.write(document);
        assertEquals(canonical, writer.write(parser.parseInto(canonical)), "Benchmark must preserve formatted content");
        var handler = new MarkdownFileHandler();
        var file = directory.resolve("document.md").toFile();
        double[] writes = new double[7], reads = new double[7];
        for (int i = -3; i < writes.length; i++) {
            long start = System.nanoTime();
            handler.write(file, canonical);
            double write = (System.nanoTime() - start) / 1e6;
            start = System.nanoTime();
            String loaded = handler.read(file);
            double read = (System.nanoTime() - start) / 1e6;
            assertEquals(canonical, loaded);
            if (i >= 0) { writes[i] = write; reads[i] = read; }
        }
        report.metric("write_file_cached", "ms", writes);
        report.metric("read_file_cached", "ms", reads);
        report.write(source.length(), paragraphs, document.getDecorations().size());
    }

    private static double[] measure(Supplier<?> operation) {
        double[] samples = new double[7];
        for (int i = -3; i < samples.length; i++) {
            long start = System.nanoTime();
            consumed = operation.get();
            if (i >= 0) samples[i] = (System.nanoTime() - start) / 1e6;
        }
        assertNotNull(consumed);
        return samples;
    }
}
