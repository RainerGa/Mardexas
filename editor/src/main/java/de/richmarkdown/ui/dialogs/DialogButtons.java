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

package de.richmarkdown.ui.dialogs;

import de.richmarkdown.i18n.I18n;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

/**
 * Zentral definierte, lokalisierte JavaFX-Buttontypen für die Dialoge der
 * Anwendung.
 *
 * <p>Die gemeinsamen Instanzen sorgen dafür, dass Beschriftung und
 * {@link ButtonBar.ButtonData} zwischen Dialogen konsistent sind. Die Labels
 * werden beim Laden dieser Klasse anhand der zu diesem Zeitpunkt aktiven
 * Sprache aufgelöst.
 */
public final class DialogButtons {

    /** Bestätigungsaktion mit OK-Semantik. */
    public static final ButtonType OK =
            new ButtonType(I18n.text("dialog.ok"), ButtonBar.ButtonData.OK_DONE);
    /** Druckaktion mit OK-Semantik für den Druckdialog. */
    public static final ButtonType PRINT =
            new ButtonType(I18n.text("dialog.print.print"), ButtonBar.ButtonData.OK_DONE);
    /** Abbruchaktion mit Cancel-Semantik. */
    public static final ButtonType CANCEL =
            new ButtonType(I18n.text("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
    /** Schließaktion ohne bestätigende Semantik. */
    public static final ButtonType CLOSE =
            new ButtonType(I18n.text("dialog.close"), ButtonBar.ButtonData.CANCEL_CLOSE);
    /** Zustimmung zum Verwerfen nicht gespeicherter Änderungen. */
    public static final ButtonType DISCARD =
            new ButtonType(I18n.text("confirm.yes"), ButtonBar.ButtonData.YES);
    /** Ablehnung des Verwerfens nicht gespeicherter Änderungen. */
    public static final ButtonType KEEP =
            new ButtonType(I18n.text("confirm.no"), ButtonBar.ButtonData.NO);

    private DialogButtons() {
    }
}
