package de.richmarkdown.io;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Bounded session state stored in the application profile, never in user documents. */
public final class WorkspaceSession {
    public static final int MAX_DOCUMENTS = 100;
    private static final long MAX_BYTES = 16 * 1024 * 1024;
    public record Entry(String path, int caret, double scroll, boolean readOnly) {
        public Entry {
            if (path == null || path.isBlank() || caret < 0 || !Double.isFinite(scroll) || scroll < 0) {
                throw new IllegalArgumentException("Invalid session entry");
            }
        }
    }
    public record State(List<Entry> entries, int active) {
        public State {
            entries = List.copyOf(entries);
            if (entries.size() > MAX_DOCUMENTS || active < 0 || active >= Math.max(1, entries.size())) {
                throw new IllegalArgumentException("Invalid session size or selection");
            }
        }
    }
    private final Path file;
    public WorkspaceSession(Path file) { this.file = file.toAbsolutePath().normalize(); }

    public State load() throws IOException {
        if (!Files.exists(file)) return new State(List.of(), 0);
        if (Files.size(file) > MAX_BYTES) throw new IOException("Session file is too large");
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(reader); }
        try {
            int count = Integer.parseInt(properties.getProperty("count", "0"));
            if (count < 0 || count > MAX_DOCUMENTS) throw new IllegalArgumentException("Session size");
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(properties.getProperty(i + ".path"),
                        Integer.parseInt(properties.getProperty(i + ".caret", "0")),
                        Double.parseDouble(properties.getProperty(i + ".scroll", "0")),
                        Boolean.parseBoolean(properties.getProperty(i + ".readOnly", "false"))));
            }
            return new State(entries, Integer.parseInt(properties.getProperty("active", "0")));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid session file", exception);
        }
    }

    public void save(State state) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("count", Integer.toString(state.entries().size()));
        properties.setProperty("active", Integer.toString(state.active()));
        for (int i = 0; i < state.entries().size(); i++) {
            Entry entry = state.entries().get(i);
            properties.setProperty(i + ".path", entry.path());
            properties.setProperty(i + ".caret", Integer.toString(entry.caret()));
            properties.setProperty(i + ".scroll", Double.toString(entry.scroll()));
            properties.setProperty(i + ".readOnly", Boolean.toString(entry.readOnly()));
        }
        Path parent = file.getParent();
        if (parent == null) throw new IOException("Session file needs a parent directory");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "session-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, "Mardexas workspace");
            }
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
