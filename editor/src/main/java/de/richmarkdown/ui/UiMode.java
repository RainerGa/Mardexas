package de.richmarkdown.ui;

/** Presentation preference only; never part of a Markdown document. */
public enum UiMode {
    CLASSIC, RIBBON;

    public static UiMode parse(String value) {
        if (value == null) return CLASSIC;
        try { return valueOf(value.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { return CLASSIC; }
    }
}
