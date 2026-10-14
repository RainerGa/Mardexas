package de.richmarkdown.markdown;

import java.util.List;

/**
 * Blockzitat, das eine geordnete Folge beliebiger Markdown-Blöcke enthalten kann.
 *
 * <p>Die rekursive Struktur ermöglicht unter anderem Zitate mit mehreren
 * Absätzen, Listen oder weiteren Zitaten. Die Kinderliste wird bei Erzeugung
 * defensiv kopiert.
 */
public class QuoteBlock extends Block {

    private final List<Block> children;

    /**
     * Erstellt ein Zitat aus den enthaltenen Blöcken.
     *
     * @param children verschachtelte Blöcke in ihrer Dokumentreihenfolge
     */
    public QuoteBlock(List<Block> children) {
        this.children = List.copyOf(children);
    }

    /**
     * Liefert die unveränderliche Folge der Zitatblöcke.
     *
     * @return enthaltene Blöcke in ihrer Dokumentreihenfolge
     */
    public List<Block> getChildren() {
        return children;
    }
}
