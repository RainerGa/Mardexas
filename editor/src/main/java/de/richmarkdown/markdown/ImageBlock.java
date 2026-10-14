package de.richmarkdown.markdown;

/**
 * Markdown-Bildverweis.
 *
 * <p>Das aktuelle Rich-Text-Document-Modell stellt die Ziel-URL bereit, bildet
 * den Markdown-Alt-Text jedoch nicht ab. Beim Round-Trip kann deshalb der
 * Alt-Text nicht erhalten werden.
 */
public class ImageBlock extends Block {

    private final String url;

    /**
     * Erstellt einen Bildverweis.
     *
     * @param url Zieladresse des Bildes; kann bei unvollständigem Quelldokument
     *            {@code null} sein
     */
    public ImageBlock(String url) {
        this.url = url;
    }

    /**
     * Liefert die Zieladresse des Bildes.
     *
     * @return Bild-URL oder {@code null}, falls keine hinterlegt ist
     */
    public String getUrl() {
        return url;
    }
}
