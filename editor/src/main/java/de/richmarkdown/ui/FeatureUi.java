package de.richmarkdown.ui;

import de.richmarkdown.i18n.I18n;
import de.richmarkdown.io.WorkspaceSession;
import de.richmarkdown.settings.ApplicationSettings;
import java.nio.file.Path;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.stage.Window;

/** Small shared UI helpers for application features; no document state. */
public final class FeatureUi {
    private FeatureUi() { }
    public static String text(String german, String english) {
        return "de".equals(I18n.currentLanguageCode()) ? german : english;
    }
    public static Path profileDirectory() {
        String override = System.getProperty("rme.config.dir");
        Path base = override != null && !override.isBlank() ? Path.of(override)
                : ApplicationSettings.defaultConfigDirectory(System.getProperty("os.name", ""),
                System.getProperty("user.home", ""), System.getenv("XDG_CONFIG_HOME"), System.getenv("APPDATA"));
        return base.resolve("RichMarkDownEditor");
    }
    public static Button button(String label, String explanation, Runnable action) {
        Button button = new Button(label);
        button.setTooltip(new Tooltip(explanation));
        button.setAccessibleText(explanation);
        button.setOnAction(event -> action.run());
        return button;
    }
    public static MenuItem item(String label, Runnable action) {
        MenuItem item = new MenuItem(label);
        item.setOnAction(event -> action.run());
        return item;
    }
    public static void error(Window owner, Exception exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (owner != null) alert.initOwner(owner);
        alert.setTitle(text("Aktion fehlgeschlagen", "Action failed"));
        alert.setHeaderText(null);
        alert.setContentText(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        alert.show();
    }
}
