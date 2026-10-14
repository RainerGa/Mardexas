package de.richmarkdown.markdown;

import java.util.List;

/**
 * Mehrzeiliger Markdown-Codeblock mit optionaler Fence-Info-Zeichenkette.
 *
 * <p>Der Inhalt wird zeilenweise und ohne Syntax-Highlighting-Metadaten
 * gespeichert. Eine nicht angegebene Sprache wird als leere Zeichenkette
 * repräsentiert.
 */
public class CodeBlockBlock extends Block {

    private final String language;
    private final List<String> lines;

    /**
     * Erstellt einen Codeblock ohne Info-String.
     *
     * @param lines Codezeilen in unveränderlicher Ausgabereihenfolge
     */
    public CodeBlockBlock(List<String> lines) {
        this("", lines);
    }

    /**
     * Erstellt einen Codeblock mit optionalem Info-String.
     *
     * @param language Sprache oder anderer Fence-Info-Text; {@code null} wird
     *                 als leerer Info-String normalisiert
     * @param lines Codezeilen in Ausgabereihenfolge
     */
    public CodeBlockBlock(String language, List<String> lines) {
        this.language = language == null ? "" : language;
        this.lines = List.copyOf(lines);
    }

    /**
     * Liefert den Info-String der Code-Fence (z. B. {@code java}).
     *
     * @return Info-String oder leere Zeichenkette, falls keiner angegeben war
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Liefert die unveränderliche Folge der Codezeilen.
     *
     * @return Codezeilen in ihrer ursprünglichen Reihenfolge
     */
    public List<String> getLines() {
        return lines;
    }
}
