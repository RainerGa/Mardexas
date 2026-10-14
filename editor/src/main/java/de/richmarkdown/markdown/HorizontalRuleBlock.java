package de.richmarkdown.markdown;

/**
 * Semantische horizontale Trennlinie, die beim Schreiben als Markdown-
 * Horizontal-Rule-Marker ausgegeben wird.
 *
 * <p>Der Block trägt keine konfigurierbaren Daten; die konkrete Schreibweise
 * wird vom {@link MarkdownWriter} bestimmt.
 */
public class HorizontalRuleBlock extends Block {

    /** Erstellt eine horizontale Trennlinie ohne zusätzliche Attribute. */
    public HorizontalRuleBlock() {
    }
}
