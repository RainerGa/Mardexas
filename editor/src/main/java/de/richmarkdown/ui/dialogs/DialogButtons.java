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
