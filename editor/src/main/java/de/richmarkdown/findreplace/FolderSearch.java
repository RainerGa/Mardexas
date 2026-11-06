package de.richmarkdown.findreplace;

import java.io.IOException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded, cancellable raw Markdown search. No syntax or metadata is added to documents. */
public final class FolderSearch {
    public static final int MAX_FILES = 2_000;
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    public static final int MAX_TOTAL_BYTES = 32 * 1024 * 1024;
    public static final int MAX_MATCHES = 10_000;
    public record Query(String text, boolean regex, boolean matchCase, boolean wholeWords) {
        public Query {
            Objects.requireNonNull(text);
            if (text.isEmpty() || text.length() > 4_096) throw new IllegalArgumentException("Suchbegriff muss 1 bis 4096 Zeichen enthalten");
        }
        public Pattern pattern() {
            return Pattern.compile(regex ? text : Pattern.quote(text), Pattern.MULTILINE
                    | (matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
    }
    public record Hit(int start, int end, int line, String text, String context) { }
    public record FileMatches(Path file, String original, List<Hit> hits) {
        public FileMatches { file = file.toAbsolutePath().normalize(); hits = List.copyOf(hits); }
        @Override public String toString() { return file.getFileName() + " (" + hits.size() + ")"; }
    }
    public record Result(List<FileMatches> files, List<String> warnings, int scannedFiles, int matches) {
        public Result { files = List.copyOf(files); warnings = List.copyOf(warnings); }
    }
    private FolderSearch() { }

    public static Result search(Path directory, Query query) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Kein reguläres Suchverzeichnis");
        Pattern pattern = query.pattern();
        List<FileMatches> results = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int scanned = 0, totalBytes = 0, totalMatches = 0;
        try (var paths = Files.walk(directory, 32)) {
            var iterator = paths.iterator();
            while (iterator.hasNext()) {
                cancelled();
                Path path = iterator.next();
                Path name = path.getFileName();
                if (name == null) continue;
                String filename = name.toString().toLowerCase(Locale.ROOT);
                if (!(filename.endsWith(".md") || filename.endsWith(".markdown"))
                        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                if (scanned >= MAX_FILES) { warnings.add("Dateigrenze erreicht (2000)."); break; }
                scanned++;
                try {
                    if (Files.size(path) > MAX_FILE_BYTES) { warnings.add(path + ": größer als 2 MiB; übersprungen."); continue; }
                    byte[] bytes;
                    try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(MAX_FILE_BYTES + 1); }
                    if (bytes.length > MAX_FILE_BYTES) { warnings.add(path + ": Datei zu groß; übersprungen."); continue; }
                    if (totalBytes + bytes.length > MAX_TOTAL_BYTES) { warnings.add("Speichergrenze erreicht (32 MiB)."); break; }
                    totalBytes += bytes.length;
                    String source = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                    List<Hit> hits = find(source, pattern, query.wholeWords(), MAX_MATCHES - totalMatches);
                    if (!hits.isEmpty()) { results.add(new FileMatches(path, source, hits)); totalMatches += hits.size(); }
                    if (totalMatches >= MAX_MATCHES) { warnings.add("Treffergrenze erreicht (10000)."); break; }
                } catch (IOException | SearchLimitException error) {
                    warnings.add(path + ": " + error.getMessage());
                }
            }
        }
        results.sort(Comparator.comparing(file -> file.file().toString()));
        return new Result(results, warnings, scanned, totalMatches);
    }

    private static List<Hit> find(String source, Pattern pattern, boolean words, int limit) throws IOException {
        Matcher matcher = pattern.matcher(new GuardedText(source));
        List<Hit> hits = new ArrayList<>();
        int line = 1, lineOffset = 0;
        try {
            while (matcher.find()) {
                cancelled();
                if (matcher.start() == matcher.end()) continue;
                if (words && (!boundary(source, matcher.start()) || !boundary(source, matcher.end()))) continue;
                while (lineOffset < matcher.start()) if (source.charAt(lineOffset++) == '\n') line++;
                String context = source.substring(Math.max(0, matcher.start() - 45), Math.min(source.length(), matcher.end() + 45))
                        .replace('\n', ' ').replace('\r', ' ');
                hits.add(new Hit(matcher.start(), matcher.end(), line, source.substring(matcher.start(), matcher.end()), context));
                if (hits.size() >= limit) break;
            }
        } catch (StackOverflowError error) { throw new SearchLimitException("Regulärer Ausdruck ist zu komplex"); }
        return List.copyOf(hits);
    }

    /** Preview uses the exact same regex semantics as the replacement operation. */
    public static String replacement(FileMatches file, Query query, String replacement) {
        Objects.requireNonNull(replacement);
        StringBuilder result = new StringBuilder(file.original().length());
        Matcher matcher = query.pattern().matcher(new GuardedText(file.original()));
        int end = 0;
        var allowed = new java.util.HashSet<Long>();
        for (Hit hit : file.hits()) allowed.add(((long) hit.start() << 32) | (hit.end() & 0xffffffffL));
        try {
            while (matcher.find()) {
                if (matcher.start() == matcher.end() || query.wholeWords()
                        && (!boundary(file.original(), matcher.start()) || !boundary(file.original(), matcher.end()))) continue;
                if (!allowed.contains(((long) matcher.start() << 32) | (matcher.end() & 0xffffffffL))) continue;
                result.append(file.original(), end, matcher.start());
                result.append(query.regex() ? expandGroups(matcher, replacement) : replacement);
                end = matcher.end();
                if (result.length() > MAX_FILE_BYTES) throw new IllegalArgumentException("Ergebnis überschreitet 2 MiB");
            }
        } catch (StackOverflowError error) { throw new SearchLimitException("Regulärer Ausdruck ist zu komplex"); }
        result.append(file.original(), end, file.original().length());
        if (result.toString().getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) throw new IllegalArgumentException("Ergebnis überschreitet 2 MiB");
        return result.toString();
    }

    private static String expandGroups(Matcher matcher, String replacement) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < replacement.length(); i++) {
            char ch = replacement.charAt(i);
            if (ch == '\\') {
                if (++i == replacement.length()) throw new IllegalArgumentException("Unvollständige Ersatz-Escape-Sequenz");
                result.append(replacement.charAt(i));
            } else if (ch == '$') {
                if (++i == replacement.length()) throw new IllegalArgumentException("Fehlende Gruppennummer");
                if (replacement.charAt(i) == '{') {
                    int close = replacement.indexOf('}', i + 1);
                    if (close < 0) throw new IllegalArgumentException("Ungültige benannte Gruppe");
                    String value = matcher.group(replacement.substring(i + 1, close));
                    if (value != null) result.append(value);
                    i = close;
                } else {
                    int group = Character.digit(replacement.charAt(i), 10);
                    if (group < 0 || group > matcher.groupCount()) throw new IllegalArgumentException("Ungültige Gruppennummer");
                    while (i + 1 < replacement.length()) {
                        int digit = Character.digit(replacement.charAt(i + 1), 10);
                        if (digit < 0 || group * 10 + digit > matcher.groupCount()) break;
                        group = group * 10 + digit; i++;
                    }
                    String value = matcher.group(group);
                    if (value != null) result.append(value);
                }
            } else result.append(ch);
        }
        return result.toString();
    }

    /** Preflight all selected files first; each individual replacement is atomic and gets a backup. */
    public static List<Path> apply(List<FileMatches> selected, Query query, String replacement) throws IOException {
        List<String> contents = new ArrayList<>();
        for (FileMatches file : selected) {
            cancelled();
            if (!Files.isRegularFile(file.file(), LinkOption.NOFOLLOW_LINKS) || !Files.isWritable(file.file())
                    || !readBounded(file.file()).equals(file.original())) {
                throw new IOException("Datei geändert, schreibgeschützt oder nicht mehr verfügbar: " + file.file());
            }
            contents.add(replacement(file, query, replacement));
        }
        List<Path> backups = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            cancelled();
            FileMatches file = selected.get(i);
            Path parent = file.file().getParent();
            if (parent == null) throw new IOException("Dateiverzeichnis fehlt");
            if (!readBounded(file.file()).equals(file.original())) throw new IOException("Datei inzwischen geändert: " + file.file());
            Path backup = Files.createTempFile(parent, file.file().getFileName() + ".before-replace-", ".bak");
            Files.copy(file.file(), backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            backups.add(backup);
            Path temporary = Files.createTempFile(parent, ".mardexas-replace-", ".tmp");
            try {
                Files.copy(file.file(), temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                Files.writeString(temporary, contents.get(i), StandardCharsets.UTF_8);
                try { Files.move(temporary, file.file(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file.file(), StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
        }
        return List.copyOf(backups);
    }

    private static String readBounded(Path file) throws IOException {
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
            if (bytes.length > MAX_FILE_BYTES) throw new IOException("Datei überschreitet 2 MiB: " + file);
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        }
    }

    private static boolean boundary(String text, int offset) {
        return offset <= 0 || offset >= text.length() || word(text.charAt(offset - 1)) != word(text.charAt(offset));
    }
    private static boolean word(char ch) { return Character.isLetterOrDigit(ch) || ch == '_'; }
    private static void cancelled() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Suche abgebrochen");
    }
    private static final class SearchLimitException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        SearchLimitException(String message) { super(message); }
    }
    /** Prevent pathological regexes from monopolizing a worker indefinitely. */
    private static final class GuardedText implements CharSequence {
        private final String text;
        private int accesses;
        GuardedText(String text) { this.text = text; }
        @Override public int length() { return text.length(); }
        @Override public char charAt(int index) {
            if (++accesses > 8_000_000 || Thread.currentThread().isInterrupted()) throw new SearchLimitException("Suchbudget überschritten / abgebrochen");
            return text.charAt(index);
        }
        @Override public CharSequence subSequence(int start, int end) { return text.subSequence(start, end); }
        @Override public String toString() { return text; }
    }
}
