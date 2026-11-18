package de.richmarkdown.io;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.Locale;

/** Resolves user paths against the document, and imports without overwriting another asset. */
public final class ImageAssets {
    private ImageAssets() { }
    public static String resolve(String source, Path documentDirectory) {
        String value = source.strip();
        if (value.isEmpty() || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            throw new IllegalArgumentException("dialog.validation.required");
        if (value.matches("^[a-zA-Z]:[\\\\/].*")) return Path.of(value).toAbsolutePath().normalize().toUri().toASCIIString();
        if (value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
            URI uri = URI.create(value);
            if (!java.util.Set.of("http", "https", "file").contains(uri.getScheme().toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("dialog.workflow.invalidTarget");
            if ((uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https")) && uri.getHost() == null)
                throw new IllegalArgumentException("dialog.workflow.invalidTarget");
            return uri.toASCIIString();
        }
        Path path = Path.of(value);
        if (!path.isAbsolute()) {
            if (documentDirectory == null) throw new IllegalArgumentException("dialog.workflow.saveFirst");
            path = documentDirectory.resolve(path);
        }
        return path.toAbsolutePath().normalize().toUri().toASCIIString();
    }
    public static Path copyInto(Path source, Path directory) throws IOException {
        Path filename = source.getFileName();
        if (filename == null) throw new IOException("Missing image filename: " + source);
        Files.createDirectories(directory);
        String name = filename.toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String suffix = dot > 0 ? name.substring(dot) : "";
        for (int number = 0; number < 10_000; number++) {
            Path target = directory.resolve(stem + (number == 0 ? "" : "-" + number) + suffix);
            if (target.toAbsolutePath().normalize().equals(source.toAbsolutePath().normalize())) return source;
            try { return Files.copy(source, target); }
            catch (FileAlreadyExistsException collision) { /* Try another name, never overwrite. */ }
        }
        throw new IOException("No unused image filename available");
    }
}
