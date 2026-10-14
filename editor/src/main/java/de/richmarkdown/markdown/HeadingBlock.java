package de.richmarkdown.markdown;

import java.util.List;

/**
 * Markdown-Überschrift mit einer Ebene von 1 bis 6 und formatiertem Inhalt.
 *
 * <p>Die Ebene ist unabhängig von den Inline-Formatierungen gespeichert.
 * Dadurch kann der Writer Überschriftenmarker und Inhalt getrennt
 * serialisieren.
 */
public class HeadingBlock extends Block {

    private final int level;
    private final List<Inline> inlines;

    /**
     * Erstellt eine Überschrift.
     *
     * @param level Markdown-Überschriftenebene im Bereich 1 bis 6
     * @param inlines formatierter Überschriftentext in Dokumentreihenfolge
     * @throws IllegalArgumentException wenn {@code level} außerhalb 1 bis 6 liegt
     */
    public HeadingBlock(int level, List<Inline> inlines) {
        if (level < 1 || level > 6) {
            throw new IllegalArgumentException("Invalid heading level: " + level);
        }
        this.level = level;
        this.inlines = List.copyOf(inlines);
    }

    /**
     * Liefert die semantische Ebene der Überschrift.
     *
     * @return Markdown-Überschriftenebene von 1 bis 6
     */
    public int getLevel() {
        return level;
    }

    /**
     * Liefert den formatierten Text der Überschrift.
     *
     * @return formatierte Inline-Elemente in Textreihenfolge
     */
    public List<Inline> getInlines() {
        return inlines;
    }
}
