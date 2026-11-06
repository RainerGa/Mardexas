package de.richmarkdown.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ExternalFileMonitorTest {
    @TempDir Path directory;
    @Test void reportsWatchedChangesAndDeletion() throws Exception {
        Path file = directory.resolve("readme.md"); Files.writeString(file, "old");
        var queue = new LinkedBlockingQueue<Path>();
        try (var monitor = new ExternalFileMonitor(queue::add)) {
            monitor.setFiles(Set.of(file)); Files.writeString(file, "new");
            assertEquals(file.toAbsolutePath(), queue.poll(5, TimeUnit.SECONDS));
            queue.clear(); Files.delete(file);
            assertEquals(file.toAbsolutePath(), queue.poll(5, TimeUnit.SECONDS));
        }
    }
    @Test void filtersUnwatchedFilesAndDisposesDirectories() throws Exception {
        var queue = new LinkedBlockingQueue<Path>();
        try (var monitor = new ExternalFileMonitor(queue::add)) {
            monitor.setFiles(Set.of(directory.resolve("watched.md")));
            Files.writeString(directory.resolve("other.md"), "ignore");
            assertNull(queue.poll(150, TimeUnit.MILLISECONDS));
            monitor.setFiles(Set.of()); Files.writeString(directory.resolve("watched.md"), "also ignore");
            assertNull(queue.poll(150, TimeUnit.MILLISECONDS));
        }
    }
    @Test void closingIsIdempotentAndRejectsNewRegistrations() throws Exception {
        var monitor = new ExternalFileMonitor(path -> { }); monitor.close(); monitor.close();
        assertThrows(java.io.IOException.class, () -> monitor.setFiles(Set.of(directory.resolve("a"))));
    }
}
