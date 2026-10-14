package de.richmarkdown.markdown;

/**
 * Zentrale kanonische Style-Mapping (PROJEKTPLAN 5.2/5.3).
 *
 * Single Source of Truth für Überschriften-Größen, Code-Block-Erkennung und
 * Schriftfamilien. Parser, Writer und UI lesen ausschließlich von hier.
 *
 * <p>Abweichung gegenüber Planfassung 4 (dokumentiert): H5 ist 15px statt 14px.
 * Die Bibliothek verwendet 14px als Default-Schriftgröße für Body-Text
 * ({@code RichTextAreaSkin.DEFAULT_FONT_SIZE}). Bei H5 = 14px wäre fetter
 * Body-Text (14px + BOLD) von H5 nicht unterscheidbar – der Writer würde
 * {@code **fett**} fälschlich als {@code ##### fett} schreiben (Round-Trip-Bruch).
 * Mit 15px sind alle Heading-Größen eindeutig gegenüber Body-Text.
 */
public final class MarkdownStyles {

    /** Default-Schriftgröße der Bibliothek für Body-Text (RichTextAreaSkin.DEFAULT_FONT_SIZE). */
    public static final double DEFAULT_FONT_SIZE = 14d;

    /** Kanonische Überschriften-Größen: H1=32, H2=24, H3=19, H4=16, H5=15, H6=13. */
    private static final double[] HEADING_SIZES = {32d, 24d, 19d, 16d, 15d, 13d};

    /** Schriftfamilie für Inline-Code und Code-Blöcke. */
    public static final String MONOSPACED = "monospaced";

    /**
     * Hintergrund-Marker für Code-Blöcke (rein dekorativ, nicht persistiert).
     * Dient der eindeutigen Code-Block-Erkennung im Writer (Plan 5.3, Regel 4).
     */
    public static final String CODE_BLOCK_BACKGROUND = "#F0F0F0";

    private MarkdownStyles() {
    }

    /**
     * Kanonische Schriftgröße für die gegebene Überschriften-Ebene.
     *
     * @param level 1..6
     * @return Größe in px, oder -1 bei ungültiger Ebene
     */
    public static double headingSize(int level) {
        if (level < 1 || level > HEADING_SIZES.length) {
            return -1;
        }
        return HEADING_SIZES[level - 1];
    }

    /**
     * Überschriften-Ebene für die gegebene Schriftgröße.
     *
     * @param fontSize Größe in px
     * @return Ebene 1..6, oder -1 wenn es keine Heading-Größe ist
     */
    public static int headingLevel(double fontSize) {
        for (int i = 0; i < HEADING_SIZES.length; i++) {
            if (Double.compare(HEADING_SIZES[i], fontSize) == 0) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * Prüft, ob die gegebene Schriftgröße eine kanonische Heading-Größe ist.
     * Body-Text (14px) ist bewusst keine Heading-Größe.
     *
     * @param fontSize Schriftgröße in Pixeln
     * @return {@code true}, wenn die Größe einer Heading-Ebene 1 bis 6 entspricht
     */
    public static boolean isHeadingSize(double fontSize) {
        return headingLevel(fontSize) > 0;
    }

    /**
     * Prüft, ob der gegebene Hintergrundwert der Code-Block-Marker ist.
     *
     * @param background Hintergrundfarbe oder Markerwert
     * @return {@code true}, wenn der Wert dem Codeblock-Marker entspricht
     */
    public static boolean isCodeBlockBackground(String background) {
        return CODE_BLOCK_BACKGROUND.equals(background);
    }
}
