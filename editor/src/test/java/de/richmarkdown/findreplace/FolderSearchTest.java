package de.richmarkdown.findreplace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FolderSearchTest {
    @TempDir Path directory;
    private FolderSearch.Query query(String text) { return new FolderSearch.Query(text, false, false, false); }
    @Test void recursiveMarkdownOnlyAndCaseAndWholeWords() throws Exception {
        Files.writeString(directory.resolve("a.md"), "Cat catapult\nCAT");
        Files.writeString(directory.resolve("ignore.txt"), "cat");
        Files.createDirectory(directory.resolve("sub"));
        Files.writeString(directory.resolve("sub/b.markdown"), "cat");
        var result = FolderSearch.search(directory, new FolderSearch.Query("cat", false, false, true));
        assertEquals(2, result.files().size()); assertEquals(3, result.matches());
        assertEquals(2, result.files().get(0).hits().get(1).line());
        assertEquals(1, FolderSearch.search(directory, new FolderSearch.Query("Cat", false, true, true)).matches());
        assertThrows(UnsupportedOperationException.class, () -> result.files().clear());
    }
    @Test void regexPreviewAndApplyAgreeWithNamedAndNumberedGroupsAndBackups() throws Exception {
        Path file = directory.resolve("a.md"); Files.writeString(file, "ab12 cd34");
        var request = new FolderSearch.Query("(?<word>[a-z]+)([0-9]+)", true, true, false);
        var found = FolderSearch.search(directory, request);
        assertEquals("12:ab 34:cd", FolderSearch.replacement(found.files().get(0), request, "$2:${word}"));
        var backups = FolderSearch.apply(found.files(), request, "$2:${word}");
        assertEquals("12:ab 34:cd", Files.readString(file));
        assertEquals("ab12 cd34", Files.readString(backups.get(0)));
    }
    @Test void literalsDoNotInterpretReplacementGroupsAndOnlySelectedFilesChange() throws Exception {
        Files.writeString(directory.resolve("a.md"), "needle"); Files.writeString(directory.resolve("b.md"), "needle");
        var found = FolderSearch.search(directory, query("needle"));
        FolderSearch.apply(List.of(found.files().get(1)), query("needle"), "$1\\literal");
        assertEquals("needle", Files.readString(directory.resolve("a.md")));
        assertEquals("$1\\literal", Files.readString(directory.resolve("b.md")));
    }
    @Test void staleOrMissingFilePreflightWritesNothing() throws Exception {
        Path a = directory.resolve("a.md"), b = directory.resolve("b.md");
        Files.writeString(a, "old"); Files.writeString(b, "old");
        var result = FolderSearch.search(directory, query("old"));
        Files.writeString(b, "changed");
        assertThrows(IOException.class, () -> FolderSearch.apply(result.files(), query("old"), "new"));
        assertEquals("old", Files.readString(a));
        Files.delete(b);
        assertThrows(IOException.class, () -> FolderSearch.apply(result.files(), query("old"), "new"));
    }
    @Test void invalidQueriesAndReplacementsAndDirectoryAreRejected() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> query(""));
        assertThrows(IllegalArgumentException.class, () -> new FolderSearch.Query("[", true, false, false).pattern());
        assertThrows(IOException.class, () -> FolderSearch.search(directory.resolve("missing"), query("a")));
        Files.writeString(directory.resolve("a.md"), "a");
        var q = new FolderSearch.Query("(a)", true, false, false);
        var file = FolderSearch.search(directory, q).files().get(0);
        assertThrows(IllegalArgumentException.class, () -> FolderSearch.replacement(file, q, "$9"));
    }
    @Test void oversizeAndInvalidUtf8AreSkippedWithWarnings() throws Exception {
        Files.write(directory.resolve("huge.md"), new byte[FolderSearch.MAX_FILE_BYTES + 1]);
        Files.write(directory.resolve("invalid.md"), new byte[] {(byte) 0xc3, 0x28});
        var result = FolderSearch.search(directory, query("a"));
        assertEquals(0, result.matches()); assertEquals(2, result.warnings().size());
    }
    @Test void zeroLengthMatchesAreNotReplaced() throws Exception {
        Path file = directory.resolve("a.md"); Files.writeString(file, "abc");
        var q = new FolderSearch.Query("^", true, false, false);
        assertEquals(0, FolderSearch.search(directory, q).matches());
    }
    @Test void interruptionIsPreservedAndStopsSearch() {
        Thread.currentThread().interrupt();
        try { assertThrows(java.io.InterruptedIOException.class, () -> FolderSearch.search(directory, query("a"))); }
        finally { Thread.interrupted(); }
    }
    @Test void symlinksAreNotFollowed() throws Exception {
        Path outside = Files.createTempDirectory("mardexas-search-outside");
        try {
            Path file = outside.resolve("private.md"); Files.writeString(file, "secret");
            Files.createSymbolicLink(directory.resolve("linked.md"), file);
            assertEquals(0, FolderSearch.search(directory, query("secret")).matches());
            Files.delete(file);
        } finally { Files.delete(outside); }
    }
    @Test void pathologicalRegexIsBounded() throws Exception {
        Files.writeString(directory.resolve("a.md"), "a".repeat(100_000) + "!");
        var result = FolderSearch.search(directory, new FolderSearch.Query("(a+)+$", true, true, false));
        assertFalse(result.warnings().isEmpty());
    }

    @Test void matchesBeyondPreviewLimitRemainUntouched() throws Exception {
        Files.writeString(directory.resolve("a.md"), "a ".repeat(FolderSearch.MAX_MATCHES + 1));
        var found = FolderSearch.search(directory, query("a"));
        assertEquals(FolderSearch.MAX_MATCHES, found.matches());
        assertTrue(FolderSearch.replacement(found.files().get(0), query("a"), "b").endsWith("b a "));
    }
}
