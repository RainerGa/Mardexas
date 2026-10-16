/*
 * Copyright (C) 2026 Rainer Garbotz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
