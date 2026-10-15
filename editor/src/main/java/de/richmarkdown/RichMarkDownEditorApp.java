package de.richmarkdown;

import de.richmarkdown.ui.EditorWindow;
import de.richmarkdown.i18n.I18n;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * JavaFX-Anwendungseinstieg und Orchestrator des initialen Programmstarts.
 *
 * <p>Initialisiert vor dem Erzeugen des Hauptfensters die Sprache anhand der
 * gespeicherten Benutzerauswahl oder der Betriebssystem-Locale. Ist die
 * System-Locale nicht unterstützt, wird zunächst eine Sprachauswahl verlangt.
 * Erst nach erfolgreicher Initialisierung wird das Hauptfenster erstellt und
 * angezeigt.
 *
 * @see EditorWindow
 * @see I18n
 */
public class RichMarkDownEditorApp extends Application {

    /** Erstellt die JavaFX-Anwendungsinstanz. */
    public RichMarkDownEditorApp() {
    }

    /**
     * Initialisiert JavaFX-Styles, Sprache und das Hauptfenster.
     *
     * @param primaryStage von JavaFX bereitgestellte primäre Stage; das
     *                     Editorfenster wird als eigene Stage erzeugt
     */
    @Override
    public void start(Stage primaryStage) {
        if (I18n.initializeForStartup(java.util.Locale.getDefault())
                && !showInitialLanguageDialog()) {
            Platform.exit();
            return;
        }
        // System-Stylesheet aktivieren, um Betriebssystem-Schriftgrößen zu übernehmen
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);

        EditorWindow window = new EditorWindow();
        window.show();
    }

    private boolean showInitialLanguageDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Choose language / Sprache auswählen");
        dialog.setHeaderText("The system language is not supported. "
                + "Please select a language. / Die Systemsprache wird nicht unterstützt. "
                + "Bitte wählen Sie eine Sprache aus.");

        ComboBox<String> languages = new ComboBox<>();
        for (String language : I18n.supportedLanguages()) {
            languages.getItems().add(I18n.text("language." + language));
        }
        languages.getSelectionModel().select(I18n.text("language.en"));

        VBox content = new VBox(10,
                new Label("Application language / Anwendungssprache:"), languages);
        content.setPadding(new Insets(8, 0, 0, 0));
        dialog.getDialogPane().setContent(content);

        ButtonType chooseButton =
                new ButtonType("Continue / Weiter", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton =
                new ButtonType("Cancel / Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(chooseButton, cancelButton);
        dialog.setResultConverter(button ->
                button == chooseButton ? languages.getValue() : null);

        var selection = dialog.showAndWait();
        if (selection.isEmpty()) {
            return false;
        }
        String selectedLabel = selection.get();
        String selectedLanguage = I18n.supportedLanguages().stream()
                .filter(language -> I18n.text("language." + language).equals(selectedLabel))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No language code found for selected label: " + selectedLabel));
        I18n.selectInitialLanguage(selectedLanguage);
        return true;
    }

    /**
     * Startet die JavaFX-Laufzeit und delegiert den Lebenszyklus an JavaFX.
     *
     * @param args Startargumente der Anwendung
     */
    public static void main(String[] args) {
        ensureJavaHomeForNativeImage();
        launch(RichMarkDownEditorApp.class, args);
    }

    /**
     * Stellt {@code java.home} im GraalVM-Native-Image sicher. Dort ist die
     * Eigenschaft zur Laufzeit nicht gesetzt ({@code null}); sobald JavaFX
     * den Swing-basierten Druckdialog öffnet, verlangt
     * {@code sun.awt.FontConfiguration} jedoch {@code java.home} und wirft
     * sonst {@code Error: java.home property not set} auf dem
     * {@code rme-print}-Thread.
     *
     * <p>Ist bereits ein Wert gesetzt (JVM-/jpackage-Start), bleibt er
     * unverändert. Im Native-Image wird auf das Verzeichnis der
     * Programmdatei zurückgegriffen – dort liegt keine {@code lib/fonts}-
     * Struktur, aber {@code FontConfiguration} findet dann schlicht keine
     * Datei (statt mit {@code Error} abzubrechen) und AWT fällt auf die
     * System-Fonts (fontconfig) zurück. Zusätzlich fängt
     * {@code EditorWindow#print()} verbleibende Druck-Fehler ab und zeigt
     * einen Hinweis statt den Thread sterben zu lassen.
     */
    private static void ensureJavaHomeForNativeImage() {
        if (System.getProperty("java.home") != null) {
            return;
        }
        try {
            String exePath = ProcessHandle.current().info().command().orElse(null);
            if (exePath != null) {
                java.nio.file.Path exe = java.nio.file.Path.of(exePath);
                java.nio.file.Path dir = exe.toAbsolutePath().getParent();
                if (dir != null && java.nio.file.Files.isDirectory(dir)) {
                    System.setProperty("java.home", dir.toString());
                    return;
                }
            }
        } catch (RuntimeException | java.io.IOError e) {
            // fällt unten auf user.dir zurück
        }
        System.setProperty("java.home", System.getProperty("user.dir", "."));
    }
}
