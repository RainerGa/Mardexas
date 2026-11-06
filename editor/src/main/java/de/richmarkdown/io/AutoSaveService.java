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

package de.richmarkdown.io;

import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.settings.ApplicationSettings;
import javafx.animation.AnimationTimer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Automatische periodische Speicherung des Dokuments in ein temporäres
 * Wiederherstellungsverzeichnis (AddFeatures #15).
 *
 * <p>Bei jeder Änderung des Dokuments wird nach einem konfigurierbaren
 * Intervall der aktuelle Markdown-Inhalt als {@code autosave.md} im
 * Anwendungskonfigurationsverzeichnis abgelegt. Beim nächsten Start wird
 * geprüft, ob eine solche Wiederherstellungsdatei existiert, und kann über
 * {@link #hasRecoveryFile()} / {@link #recover()} wiederhergestellt werden.
 *
 * <p>Die Speicherung erfolgt nur wenn das Dokument modifiziert ist und nicht
 * schreibgeschützt. Ein erfolgreicher manueller Save loescht die
 * Wiederherstellungsdatei.
 */
public final class AutoSaveService {

    private static final String AUTOSAVE_FILE_NAME = "autosave.md";
    private static final int DEFAULT_INTERVAL_MS = 300_000; // 5 Minuten

    private final MarkdownEditor editor;
    private final Path recoveryFile;
    private final AnimationTimer timer;

    /**
     * Erstellt den Autosave-Dienst. Der Timer laeuft nur, wenn das Fenster
     * sichtbar ist (kein Skin = kein Timer).
     *
     * @param editor Editor-Fassade, deren Dokument periodisch gesichert wird
     * @param intervalMillis konfiguriertes Speicherintervall
     */
    public AutoSaveService(MarkdownEditor editor, long intervalMillis) {
        this.editor = editor;
        // Wiederherstellungsdatei im plattformueblichen Config-Verzeichnis
        String os = System.getProperty("os.name", "");
        String home = System.getProperty("user.home", "");
        String xdg = System.getenv("XDG_CONFIG_HOME");
        String appData = System.getenv("APPDATA");
        Path base = ApplicationSettings.defaultConfigDirectory(os, home, xdg, appData);
        this.recoveryFile = base.resolve(AUTOSAVE_FILE_NAME);

        final long saveInterval = intervalMillis >= 10_000 ? intervalMillis : DEFAULT_INTERVAL_MS;

        this.timer = new AnimationTimer() {
            private long lastTick = 0;
            @Override
            public void handle(long now) {
                if (lastTick == 0) {
                    lastTick = now;
                    return;
                }
                if (now - lastTick >= saveInterval * 1_000_000L) {
                    lastTick = now;
                    saveIfModified();
                }
            }
        };
    }

    /**
     * Startet den periodischen Autosave-Timer. Muss auf dem FX-Thread
     * aufgerufen werden.
     */
    public void start() {
        timer.start();
    }

    /**
     * Stoppt den Timer.
     */
    public void stop() {
        timer.stop();
    }

    /**
     * Gibt an, ob eine Wiederherstellungsdatei aus einem vorherigen
     * Absturz existiert.
     *
     * @return {@code true}, wenn eine Datei zur Wiederherstellung vorliegt
     */
    public boolean hasRecoveryFile() {
        return recoveryFile != null && Files.exists(recoveryFile);
    }

    /**
     * Stellt das Dokument aus der Wiederherstellungsdatei wieder her und
     * loescht die Datei danach.
     *
     * @return {@code true}, wenn die Wiederherstellung erfolgreich war
     */
    public boolean recover() {
        if (!hasRecoveryFile()) {
            return false;
        }
        try {
            String content = Files.readString(recoveryFile, StandardCharsets.UTF_8);
            editor.setMarkdown(content);
            deleteRecoveryFile();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Loescht die Wiederherstellungsdatei (z. B. nach erfolgreichem Save). */
    public void deleteRecoveryFile() {
        if (recoveryFile != null) {
            try {
                Files.deleteIfExists(recoveryFile);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Speichert den aktuellen Markdown-Inhalt in die Wiederherstellungsdatei,
     * falls das Dokument modifiziert ist und nicht leer. Wird periodisch vom
     * Autosave-Timer aufgerufen. I/O-Fehler werden unterdrückt, damit der
     * Editor nie blockiert wird.
     */
    private void saveIfModified() {
        if (!editor.isModified()) {
            return;
        }
        try {
            String markdown = editor.getMarkdown();
            if (recoveryFile != null && markdown != null && !markdown.isBlank()) {
                Path parent = recoveryFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(recoveryFile, markdown, StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
            // Autosave darf den Editor nie blockieren
        }
    }
}
