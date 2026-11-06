package de.richmarkdown.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceSessionTest {
    @TempDir Path directory;
    @Test void missingSessionIsEmpty() throws Exception {
        assertTrue(new WorkspaceSession(directory.resolve("session")).load().entries().isEmpty());
    }
    @Test void roundTripsUnicodePathsAndLocationsAtomically() throws Exception {
        var session = new WorkspaceSession(directory.resolve("profile/session.properties"));
        var state = new WorkspaceSession.State(List.of(new WorkspaceSession.Entry("/tmp/Änderungen = Test.md", 23, 0.7, true),
                new WorkspaceSession.Entry("/tmp/second.md", 0, 0, false)), 1);
        session.save(state); assertEquals(state, session.load());
        session.save(new WorkspaceSession.State(List.of(), 0)); assertTrue(session.load().entries().isEmpty());
        try (var files = Files.list(directory.resolve("profile"))) { assertEquals(1, files.count()); }
    }
    @Test void snapshotsEntriesAndRejectsMutation() {
        var entries = new ArrayList<WorkspaceSession.Entry>();
        entries.add(new WorkspaceSession.Entry("a.md", 0, 0, false));
        var state = new WorkspaceSession.State(entries, 0); entries.clear();
        assertEquals(1, state.entries().size());
        assertThrows(UnsupportedOperationException.class, () -> state.entries().clear());
    }
    @Test void malformedAndOversizedSessionCountsAreErrors() throws Exception {
        Path file = directory.resolve("session");
        for (String value : List.of("count=101", "count=-1", "count=oops", "count=1\n0.path=a.md\n0.scroll=NaN", "count=0\nactive=2")) {
            Files.writeString(file, value); assertThrows(IOException.class, () -> new WorkspaceSession(file).load());
        }
    }
    @Test void rejectsInvalidEntriesAndPropagatesWriteFailures() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceSession.Entry("", 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceSession.Entry("a", -1, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceSession.Entry("a", 0, Double.POSITIVE_INFINITY, false));
        Path parent = directory.resolve("file"); Files.writeString(parent, "not a directory");
        assertThrows(IOException.class, () -> new WorkspaceSession(parent.resolve("session")).save(new WorkspaceSession.State(List.of(), 0)));
    }
}
