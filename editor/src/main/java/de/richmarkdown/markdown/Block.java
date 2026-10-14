package de.richmarkdown.markdown;

/**
 * Basistyp sämtlicher blockorientierter Elemente des internen Markdown-Modells.
 *
 * <p>Konkrete Untertypen repräsentieren Absätze, Überschriften, Listen,
 * Zitate, Code-Blöcke, Tabellen, Bilder und horizontale Trennlinien. Die
 * Blockfolge eines {@link MarkdownModel} erhält die Dokumentreihenfolge und
 * wird vom {@link MarkdownWriter} in Markdown serialisiert.
 *
 * <p>Block-Objekte sind Wertträger; untergeordnete Listen werden von den
 * jeweiligen Implementierungen defensiv kopiert.
 *
 * @see MarkdownModel
 * @see MarkdownParser
 * @see MarkdownWriter
 */
public abstract class Block {

    /**
     * Erstellt einen Block-Basistyp für konkrete Markdown-Elemente.
     *
     * <p>Der öffentliche Konstruktor ermöglicht Implementierungen außerhalb
     * dieses Moduls, eigene Blocktypen abzuleiten.
     */
    public Block() {
    }
}
