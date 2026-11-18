package de.richmarkdown.findreplace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FolderWorkflowTest {
    @TempDir Path directory;
    private final FolderSearch.Query query = new FolderSearch.Query("cat", false, false, false);
    @Test void patternsAndExcludedSubtreesAndProgressApplyTogether() throws Exception {
        Files.writeString(directory.resolve("one.MD"), "cat"); Files.writeString(directory.resolve("two.txt"), "cat");
        Files.createDirectories(directory.resolve("build/nested")); Files.writeString(directory.resolve("build/nested/three.md"), "cat");
        List<Integer> progress = new ArrayList<>();
        var result = FolderSearch.search(directory, query, List.of("*.md"), Set.of("build"), progress::add);
        assertEquals(1, result.matches()); assertEquals(List.of(1), progress);
        assertEquals(1, FolderSearch.search(directory, query, List.of("*.txt"), Set.of(), ignored -> { }).matches());
    }
    @Test void individualMatchChoicesAndRestorationPreserveBothVersions() throws Exception {
        Path file = directory.resolve("one.md"); Files.writeString(file, "cat cat");
        var found = FolderSearch.search(directory, query).files().getFirst();
        var selection = new FolderSearch.FileMatches(file, found.original(), List.of(found.hits().getLast()));
        List<Path> completed = new ArrayList<>();
        Path backup = FolderSearch.apply(List.of(selection), query, "dog", (original, path) -> completed.add(path)).getFirst();
        assertEquals(List.of(backup), completed); assertEquals("cat dog", Files.readString(file));
        Path undo = FolderSearch.restore(backup, selection, query, "dog");
        assertEquals("cat cat", Files.readString(file)); assertEquals("cat dog", Files.readString(undo));
    }
    @Test void restorationRejectsLaterEditsAndTamperedBackups() throws Exception {
        Path file = directory.resolve("one.md"); Files.writeString(file, "cat");
        var found = FolderSearch.search(directory, query).files().getFirst();
        Path backup = FolderSearch.apply(List.of(found), query, "dog").getFirst();
        Files.writeString(file, "my new text");
        assertThrows(IOException.class, () -> FolderSearch.restore(backup, found, query, "dog"));
        assertEquals("my new text", Files.readString(file));
        Files.writeString(file, "dog"); Files.writeString(backup, "invalid");
        assertThrows(IOException.class, () -> FolderSearch.restore(backup, found, query, "dog"));
        assertEquals("dog", Files.readString(file));
    }
}
