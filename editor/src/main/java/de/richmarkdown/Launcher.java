package de.richmarkdown;

import javafx.application.Application;

/**
 * Startklasse für das ausführbare FAT-JAR ({@code Mardexas-*-fat.jar}).
 *
 * <p>Die eigentliche Anwendung ({@link RichMarkDownEditorApp}) erweitert
 * {@code javafx.application.Application}. Stünde deren {@code main}-Methode
 * direkt als {@code Main-Class} im Manifest, meldet der JavaFX-Launcher beim
 * Classpath-Start, dass die Application-Klasse auf dem Module-Path liegen
 * müsse. Diese Klasse erweitert kein JavaFX-API und delegiert lediglich an
 * {@link RichMarkDownEditorApp#main(String[])}, sodass
 * {@code java -jar Mardexas-*-fat.jar} auch ohne Module-Path startet.</p>
 *
 * <p>Im FAT-JAR liegen alle Laufzeitabhängigkeiten in einer Datei (eigene
 * Klassen und Ressourcen, RichTextArea-Fork, Emoji sowie JavaFX-Klassen und
 * native Bibliotheken für Linux, Windows und macOS). Auf dem Classpath greifen
 * keine JPMS-Exportprüfungen, daher ist das sonst nötige {@code --add-exports}
 * für das RichTextArea-ViewModel-Paket hier nicht erforderlich.</p>
 */
public final class Launcher {

    private Launcher() {
    }

    /**
     * Startet den Editor.
     *
     * @param args Startargumente, werden an die Anwendung weitergereicht
     */
    public static void main(String[] args) {
        Application.launch(RichMarkDownEditorApp.class, args);
    }
}
