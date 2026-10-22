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

package de.richmarkdown.ui;

import de.richmarkdown.i18n.I18n;

/**
 * Zeilenumbruch-Modi nach dem Vorbild LibreOffice Writer
 * (Format → Zeilenumbruch, AddFeatures #6).
 *
 * <p>LO Writer kennt die Umbrucharten „Automatisch“ (Umbruch an der
 * Seitenbreite) und „Durchgehend“ (Umbruch erst am Rand der Arbeitsfläche).
 * Im Editor bildet der Viewport die Arbeitsfläche ab:
 * <ul>
 *   <li>{@link #AUTOMATIC}: der Text bricht an der festen Textbreite um
 *       (LO-Seitenbreite-artig), auch wenn das Fenster breiter ist.</li>
 *   <li>{@link #CONTINUOUS}: der Text bricht an der Viewport-Breite um
 *       (durchgehender Umbruch über die volle Fensterbreite).</li>
 * </ul>
 */
public enum LineWrapMode {

    /** Automatischer Umbruch an der festen Textbreite (LO-Default). */
    AUTOMATIC("menu.lineWrap.auto"),
    /** Durchgehender Umbruch an der Viewport-Breite. */
    CONTINUOUS("menu.lineWrap.continuous");

    private final String labelKey;

    LineWrapMode(String labelKey) {
        this.labelKey = labelKey;
    }

    /**
     * Liefert das lokalisierte Label des Umbruchmodus (Radio-Item-Text).
     *
     * @return aktuelles lokalisiertes Label
     */
    public String getLabel() {
        return I18n.text(labelKey);
    }

    /** Liefert das lokalisierte Label (wie {@link #getLabel()}). */
    @Override
    public String toString() {
        return getLabel();
    }
}
