package de.richmarkdown.io;

import de.richmarkdown.settings.ApplicationSettings;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

/** User snippets and templates live outside documents; document contents remain ordinary Markdown. */
public final class MarkdownLibraryStore {
    public enum Kind { SNIPPET, TEMPLATE }
    public static final int MAX_ENTRIES = 128;
    public static final int MAX_CONTENT_BYTES = 262_144;
    private static final int MAX_FILE_BYTES = MAX_CONTENT_BYTES * 4 + 4096;
    private final Path directory;
    private final Object lock = new Object();

    public record Entry(String id, String name, String shortcut, String content) {
        public Entry {
            Objects.requireNonNull(id);
            Objects.requireNonNull(name);
            Objects.requireNonNull(shortcut);
            Objects.requireNonNull(content);
            if (!id.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Ungültige Kennung");
            if (name.isBlank() || name.length() > 120 || name.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Name muss 1 bis 120 Zeichen enthalten");
            }
            if (shortcut.length() > 40 || shortcut.chars().anyMatch(Character::isWhitespace)
                    || shortcut.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Kürzel darf höchstens 40 Zeichen ohne Leerzeichen enthalten");
            }
            if (content.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
                throw new IllegalArgumentException("Inhalt überschreitet 256 KiB");
            }
        }
        @Override public String toString() { return name + (shortcut.isEmpty() ? "" : "  [" + shortcut + "]"); }
    }

    public MarkdownLibraryStore(Path root, Kind kind) {
        directory = Objects.requireNonNull(root).toAbsolutePath().normalize()
                .resolve(Objects.requireNonNull(kind).name().toLowerCase(Locale.ROOT));
    }

    public static MarkdownLibraryStore userLibrary(Kind kind) {
        String override = System.getProperty("rme.config.dir");
        Path config = override != null && !override.isBlank() ? Path.of(override)
                : ApplicationSettings.defaultConfigDirectory(System.getProperty("os.name", ""),
                System.getProperty("user.home", "."), System.getenv("XDG_CONFIG_HOME"), System.getenv("APPDATA"));
        return new MarkdownLibraryStore(config.resolve("RichMarkDownEditor").resolve("writing-library"), kind);
    }

    public List<Entry> list() throws IOException {
        synchronized (lock) {
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();
            checkDirectory();
            List<Entry> entries = new ArrayList<>();
            try (var paths = Files.newDirectoryStream(directory, "*.properties")) {
                for (Path path : paths) {
                    if (entries.size() >= MAX_ENTRIES) throw new IOException("Zu viele Bibliothekseinträge");
                    entries.add(read(path));
                }
            }
            entries.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::id));
            return List.copyOf(entries);
        }
    }

    public Entry save(String id, String name, String shortcut, String markdown) throws IOException {
        synchronized (lock) {
            Entry entry = new Entry(id == null ? UUID.randomUUID().toString() : id,
                    Objects.requireNonNull(name).strip(), Objects.requireNonNull(shortcut).strip(), markdown);
            List<Entry> existing = list();
            boolean replaces = existing.stream().anyMatch(value -> value.id().equals(entry.id()));
            if (!replaces && existing.size() >= MAX_ENTRIES) throw new IOException("Bibliothek ist voll (128 Einträge)");
            if (existing.stream().anyMatch(value -> !value.id().equals(entry.id())
                    && (value.name().equalsIgnoreCase(entry.name()) || (!entry.shortcut().isEmpty()
                    && value.shortcut().equals(entry.shortcut()))))) {
                throw new IOException("Name oder Kürzel wird bereits verwendet");
            }
            checkAncestors();
            Files.createDirectories(directory);
            checkDirectory();
            Path target = file(entry.id());
            if (Files.isSymbolicLink(target)) throw new IOException("Symbolische Verknüpfungen sind nicht zulässig");
            Properties values = new Properties();
            values.setProperty("name", entry.name());
            values.setProperty("shortcut", entry.shortcut());
            values.setProperty("markdown", entry.content());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (var writer = new OutputStreamWriter(bytes, StandardCharsets.UTF_8)) {
                values.store(writer, "Mardexas writing library; not document metadata");
            }
            Path temporary = Files.createTempFile(directory, ".write-", ".tmp");
            try {
                Files.write(temporary, bytes.toByteArray());
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return entry;
        }
    }

    public void delete(String id) throws IOException {
        synchronized (lock) {
            Path target = file(id);
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
            checkDirectory();
            if (Files.isSymbolicLink(target)) throw new IOException("Symbolische Verknüpfungen sind nicht zulässig");
            Files.deleteIfExists(target);
        }
    }

    /** Reads an explicitly selected Markdown file with a fixed memory bound. */
    public static String importMarkdown(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_CONTENT_BYTES) {
            throw new IOException("Keine lesbare Markdown-Datei bis 256 KiB");
        }
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(MAX_CONTENT_BYTES + 1);
            if (bytes.length > MAX_CONTENT_BYTES) throw new IOException("Datei überschreitet 256 KiB");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private Entry read(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_FILE_BYTES) {
            throw new IOException("Ungültiger Bibliothekseintrag: " + path.getFileName());
        }
        byte[] bytes;
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_FILE_BYTES + 1);
        }
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("Bibliothekseintrag zu groß");
        Properties values = new Properties();
        try (var reader = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            values.load(reader);
            Path name = path.getFileName();
            if (name == null) throw new IOException("Bibliothekseintrag benötigt einen Dateinamen");
            String filename = name.toString();
            return new Entry(filename.substring(0, filename.length() - ".properties".length()),
                    values.getProperty("name", ""), values.getProperty("shortcut", ""),
                    values.getProperty("markdown", ""));
        } catch (IllegalArgumentException error) {
            throw new IOException("Beschädigter Bibliothekseintrag: " + path.getFileName(), error);
        }
    }

    private Path file(String id) {
        if (id == null || !id.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Ungültige Kennung");
        return directory.resolve(id + ".properties");
    }

    private void checkAncestors() throws IOException {
        // Reject symlink ancestors so a manipulated configuration cannot redirect writes.
        for (Path path = directory; path != null; path = path.getParent()) {
            if (Files.isSymbolicLink(path)) throw new IOException("Bibliothek darf keine symbolischen Verknüpfungen enthalten");
        }
    }

    private void checkDirectory() throws IOException {
        checkAncestors();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Bibliothek ist kein Verzeichnis");
    }
}
