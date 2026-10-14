package de.richmarkdown.markdown;

import java.util.Objects;

/**
 * Unveränderlicher formatierter Textlauf innerhalb eines Markdown-Blocks.
 *
 * <p>Ein Inline enthält den Text sowie unabhängige Formatierungsmerkmale für
 * Fett, Kursiv, Unterstreichen, Durchstreichen und Inline-Code. Ein optionaler
 * URL-Wert kennzeichnet einen Hyperlink. Kombinationen wie fett und kursiv
 * werden durch gleichzeitig gesetzte Merkmale dargestellt, nicht durch
 * verschachtelte Inline-Objekte.
 *
 * <p>Instanzen werden über {@link #text(String)} oder den Fluent Builder
 * {@link #builder(String)} erzeugt. Der Builder verlangt einen nicht-null
 * Textwert; der Text selbst darf leer sein.
 *
 * @see Block
 * @see MarkdownWriter
 */
public class Inline {

    private final String text;
    private final boolean bold;
    private final boolean italic;
    private final boolean underline;
    private final boolean strikethrough;
    private final boolean code;
    private final String url;

    private Inline(Builder builder) {
        this.text = builder.text;
        this.bold = builder.bold;
        this.italic = builder.italic;
        this.underline = builder.underline;
        this.strikethrough = builder.strikethrough;
        this.code = builder.code;
        this.url = builder.url;
    }

    /**
     * Erstellt einen unformatierten Textlauf.
     *
     * @param text Textinhalt; darf nicht {@code null} sein
     * @return Inline ohne Formatierungsmerkmale
     * @throws NullPointerException wenn {@code text} null ist
     */
    public static Inline text(String text) {
        return new Builder(text).build();
    }

    /**
     * Liefert den Textinhalt dieses Laufs.
     *
     * @return unveränderter Textinhalt
     */
    public String getText() {
        return text;
    }

    /**
     * Gibt an, ob Fettschrift aktiviert ist.
     *
     * @return {@code true}, wenn Fettschrift aktiviert ist
     */
    public boolean isBold() {
        return bold;
    }

    /**
     * Gibt an, ob Kursivschrift aktiviert ist.
     *
     * @return {@code true}, wenn Kursivschrift aktiviert ist
     */
    public boolean isItalic() {
        return italic;
    }

    /**
     * Gibt an, ob Unterstreichung aktiviert ist.
     *
     * @return {@code true}, wenn Unterstreichung aktiviert ist
     */
    public boolean isUnderline() {
        return underline;
    }

    /**
     * Gibt an, ob Durchstreichung aktiviert ist.
     *
     * @return {@code true}, wenn Durchstreichung aktiviert ist
     */
    public boolean isStrikethrough() {
        return strikethrough;
    }

    /**
     * Gibt an, ob Inline-Code-Formatierung aktiviert ist.
     *
     * @return {@code true}, wenn Inline-Code-Formatierung aktiviert ist
     */
    public boolean isCode() {
        return code;
    }

    /**
     * Liefert die Link-URL oder {@code null}, wenn kein Hyperlink vorliegt.
     *
     * @return URL des Links oder {@code null}
     */
    public String getUrl() {
        return url;
    }

    /**
     * Prüft, ob mindestens ein Formatierungsmerkmal oder eine URL gesetzt ist.
     *
     * @return {@code true}, wenn der Lauf nicht als einfacher Text formatiert ist
     */
    public boolean hasFormatting() {
        return bold || italic || underline || strikethrough || code || url != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Inline inline = (Inline) o;
        return bold == inline.bold
                && italic == inline.italic
                && underline == inline.underline
                && strikethrough == inline.strikethrough
                && code == inline.code
                && Objects.equals(text, inline.text)
                && Objects.equals(url, inline.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, bold, italic, underline, strikethrough, code, url);
    }

    @Override
    public String toString() {
        return "Inline{" + text + ", bold=" + bold + ", italic=" + italic
                + ", underline=" + underline + ", strike=" + strikethrough
                + ", code=" + code + ", url=" + url + '}';
    }

    /**
     * Erstellt einen Fluent Builder für einen formatierten Textlauf.
     *
     * @param text Textinhalt; darf nicht {@code null} sein
     * @return neuer Builder
     * @throws NullPointerException wenn {@code text} null ist
     */
    public static Builder builder(String text) {
        return new Builder(text);
    }

    /**
     * Fluent Builder zum Setzen der unabhängigen Formatierungsmerkmale eines
     * {@link Inline}-Textlaufs.
     *
     * <p>Alle Setter geben dieselbe Builder-Instanz zurück; {@link #build()}
     * erzeugt eine unveränderliche Kopie des bis dahin konfigurierten Zustands.
     */
    public static final class Builder {
        private final String text;
        private boolean bold;
        private boolean italic;
        private boolean underline;
        private boolean strikethrough;
        private boolean code;
        private String url;

        private Builder(String text) {
            this.text = Objects.requireNonNull(text);
        }

        /**
         * Aktiviert oder deaktiviert Fettschrift.
         *
         * @param value {@code true} zum Aktivieren
         * @return dieser Builder für weitere Konfiguration
         */
        public Builder bold(boolean value) {
            this.bold = value;
            return this;
        }

        /**
         * Aktiviert oder deaktiviert Kursivschrift.
         *
         * @param value {@code true} zum Aktivieren
         * @return dieser Builder für weitere Konfiguration
         */
        public Builder italic(boolean value) {
            this.italic = value;
            return this;
        }

        /**
         * Aktiviert oder deaktiviert Unterstreichung.
         *
         * @param value {@code true} zum Aktivieren
         * @return dieser Builder für weitere Konfiguration
         */
        public Builder underline(boolean value) {
            this.underline = value;
            return this;
        }

        /**
         * Aktiviert oder deaktiviert Durchstreichung.
         *
         * @param value {@code true} zum Aktivieren
         * @return dieser Builder für weitere Konfiguration
         */
        public Builder strikethrough(boolean value) {
            this.strikethrough = value;
            return this;
        }

        /**
         * Aktiviert oder deaktiviert Inline-Code-Formatierung.
         *
         * @param value {@code true} zum Aktivieren
         * @return dieser Builder für weitere Konfiguration
         */
        public Builder code(boolean value) {
            this.code = value;
            return this;
        }

        /**
         * Setzt die Link-URL.
         *
         * @param value URL oder {@code null}, um den Linkstatus zu entfernen
         * @return dieser Builder für weitere Konfiguration
         */
        public Builder url(String value) {
            this.url = value;
            return this;
        }

        /**
         * Erzeugt einen unveränderlichen Textlauf mit den aktuellen Werten.
         *
         * @return fertig konfiguriertes Inline-Element
         */
        public Inline build() {
            return new Inline(this);
        }
    }
}
