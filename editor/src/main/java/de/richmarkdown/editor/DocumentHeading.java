package de.richmarkdown.editor;

/**
 * Ein Eintrag der Dokumentstruktur: Überschriften-Ebene, Text und
 * Caret-Position im Dokument (PROJEKTPLAN 7.1, Nachtrag).
 *
 * @param level    Überschriften-Ebene 1..6
 * @param text     Überschriftentext
 * @param position Caret-Position des Absatzanfangs
 */
public record DocumentHeading(int level, String text, int position) {
}
