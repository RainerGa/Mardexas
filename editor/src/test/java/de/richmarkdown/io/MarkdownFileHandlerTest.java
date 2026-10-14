package de.richmarkdown.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD Phase 5: MarkdownFileHandler – UTF-8 Datei-I/O (PROJEKTPLAN 5.2, 7.1).
 */
class MarkdownFileHandlerTest {

    private final MarkdownFileHandler handler = new MarkdownFileHandler();

    @TempDir
    Path tempDir;

    @Test
    void writesAndReadsContent() throws IOException {
        File file = tempDir.resolve("test.md").toFile();
        String content = "# Title\n\nHello world";

        handler.write(file, content);

        assertTrue(file.exists());
        assertEquals(content, handler.read(file));
    }

    @Test
    void writesUtf8WithSpecialCharacters() throws IOException {
        File file = tempDir.resolve("utf8.md").toFile();
        String content = "Überraschung: naïve café — 日本語 🎉 **fett**";

        handler.write(file, content);

        // Rohbytes prüfen: UTF-8 ohne BOM
        byte[] bytes = Files.readAllBytes(file.toPath());
        assertEquals(content, new String(bytes, StandardCharsets.UTF_8));
        assertFalse(new String(bytes, StandardCharsets.UTF_8).startsWith("\uFEFF"),
                "no BOM expected");
        assertEquals(content, handler.read(file));
    }

    @Test
    void overwritesExistingFile() throws IOException {
        File file = tempDir.resolve("overwrite.md").toFile();
        handler.write(file, "first");
        handler.write(file, "second");

        assertEquals("second", handler.read(file));
    }

    @Test
    void createsParentDirectories() throws IOException {
        File file = tempDir.resolve("sub/dir/test.md").toFile();

        handler.write(file, "content");

        assertTrue(file.exists());
        assertEquals("content", handler.read(file));
    }

    @Test
    void normalizesWindowsLineEndingsOnRead() throws IOException {
        File file = tempDir.resolve("crlf.md").toFile();
        Files.writeString(file.toPath(), "line1\r\nline2\r\n", StandardCharsets.UTF_8);

        assertEquals("line1\nline2\n", handler.read(file));
    }

    @Test
    void normalizesOldMacLineEndingsOnRead() throws IOException {
        File file = tempDir.resolve("cr.md").toFile();
        Files.writeString(file.toPath(), "line1\rline2\r", StandardCharsets.UTF_8);

        assertEquals("line1\nline2\n", handler.read(file));
    }

    @Test
    void writesUnixLineEndings() throws IOException {
        File file = tempDir.resolve("unix.md").toFile();
        handler.write(file, "line1\nline2");

        String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertFalse(raw.contains("\r"), "no CR expected in written file");
    }

    @Test
    void readNonExistentFileThrows() {
        File file = tempDir.resolve("missing.md").toFile();
        assertThrows(IOException.class, () -> handler.read(file));
    }

    @Test
    void readEmptyFileReturnsEmptyString() throws IOException {
        File file = tempDir.resolve("empty.md").toFile();
        Files.writeString(file.toPath(), "", StandardCharsets.UTF_8);

        assertEquals("", handler.read(file));
    }

    @Test
    void roundTripsMarkdownDocument() throws IOException {
        File file = tempDir.resolve("doc.md").toFile();
        String markdown = """
                # Title

                Paragraph with **bold** and *italic*.

                - item one
                - item two

                > quote

                ```
                code
                ```
                """;

        handler.write(file, markdown);

        assertEquals(markdown, handler.read(file));
    }

    @Test
    void writeToDirectoryThrows() throws IOException {
        File dir = tempDir.resolve("adir").toFile();
        assertTrue(dir.mkdirs());
        assertThrows(IOException.class, () -> handler.write(dir, "content"));
    }

    @Test
    void handlesLargeFile() throws IOException {
        File file = tempDir.resolve("large.md").toFile();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append("- item ").append(i).append('\n');
        }
        String content = sb.toString();

        handler.write(file, content);

        assertEquals(content, handler.read(file));
    }
}
