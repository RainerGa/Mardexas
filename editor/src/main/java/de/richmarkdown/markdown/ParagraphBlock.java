package de.richmarkdown.markdown;

import java.util.List;

/**
 * Normaler Textabsatz mit einer geordneten Folge formatierter Inline-Läufe.
 *
 * <p>Inline-Elemente bleiben getrennt, damit der Writer Textformatierungen
 * und Links ohne Verlust serialisieren kann. Die Liste wird bei Erzeugung
 * defensiv kopiert.
 */
public class ParagraphBlock extends Block {

    private final List<Inline> inlines;

    /**
     * Erstellt einen Absatz.
     *
     * @param inlines formatierte Textläufe in ihrer Dokumentreihenfolge
     */
    public ParagraphBlock(List<Inline> inlines) {
        this.inlines = List.copyOf(inlines);
    }

    /**
     * Liefert die unveränderliche Inline-Folge des Absatzes.
     *
     * @return Inline-Elemente in ihrer Textreihenfolge
     */
    public List<Inline> getInlines() {
        return inlines;
    }
}
