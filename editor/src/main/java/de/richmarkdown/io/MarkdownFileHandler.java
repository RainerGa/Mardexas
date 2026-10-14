package de.richmarkdown.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Datei-I/O für Markdown-Dokumente (PROJEKTPLAN 5.2, 7.1).
 *
 * <ul>
 *   <li>Immer UTF-8 (ohne BOM)</li>
 *   <li>Zeilenenden werden beim Lesen auf {@code \n} normalisiert (CRLF/CR)</li>
 *   <li>Beim Schreiben immer Unix-Zeilenenden ({@code \n})</li>
 *   <li>Fehlende Verzeichnisse werden beim Schreiben angelegt</li>
 * </ul>
 */
public class MarkdownFileHandler {

    /** Erstellt einen zustandslosen UTF-8-Markdown-Dateidienst. */
    public MarkdownFileHandler() {
    }

    /**
     * Liest eine Markdown-Datei als UTF-8 und normalisiert die Zeilenenden.
     *
     * @param file zu lesende Datei
     * @return Dateiinhalt mit {@code \n} als einheitlichem Zeilentrenner
     * @throws IOException wenn die Datei nicht gelesen werden kann
     */
    public String read(File file) throws IOException {
        if (file.isDirectory()) {
            throw new IOException("Not a file: " + file);
        }
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        return normalizeLineEndings(content);
    }

    /**
     * Schreibt Markdown-Text als UTF-8 (ohne BOM, Unix-Zeilenenden).
     * Fehlende Verzeichnisse werden angelegt.
     *
     * @param file Zieldatei
     * @param content zu speichernder Markdown-Text
     * @throws IOException wenn die Datei nicht geschrieben werden kann
     */
    public void write(File file, String content) throws IOException {
        if (file.isDirectory()) {
            throw new IOException("Is a directory: " + file);
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directories: " + parent);
        }
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
    }

    private String normalizeLineEndings(String content) {
        return content.replace("\r\n", "\n").replace("\r", "\n");
    }
}
