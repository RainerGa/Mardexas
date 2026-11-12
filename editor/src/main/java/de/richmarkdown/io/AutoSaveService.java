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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
public final class AutoSaveService implements AutoCloseable {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(AutoSaveService.class.getName());
    private static final String AUTOSAVE_FILE_NAME = "autosave.md";
    private static final int DEFAULT_INTERVAL_MS = 300_000; // 5 Minuten

    private final MarkdownEditor editor;
    private final Path recoveryFile;
    private final AnimationTimer timer;
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "mardexas-recovery"); thread.setDaemon(true); return thread;
    });
    private java.util.concurrent.CompletableFuture<Void> pending = java.util.concurrent.CompletableFuture.completedFuture(null);
    private boolean saving;

    /** Completion barrier for hosts/tests; never wait on it from the FX thread. */
    public java.util.concurrent.CompletableFuture<Void> pendingWrite() { return pending.copy(); }

    /**
     * Erstellt den Autosave-Dienst. Der Timer laeuft nur, wenn das Fenster
     * sichtbar ist (kein Skin = kein Timer).
     *
     * @param editor Editor-Fassade, deren Dokument periodisch gesichert wird
     * @param intervalMillis konfiguriertes Speicherintervall
     */
    public AutoSaveService(MarkdownEditor editor, long intervalMillis) {
        this(editor, intervalMillis, defaultRecoveryFile());
    }

    /** Allows tests to isolate recovery data without changing process-wide properties. */
    AutoSaveService(MarkdownEditor editor, long intervalMillis, Path recoveryFile) {
        this.editor = editor;
        this.recoveryFile = java.util.Objects.requireNonNull(recoveryFile);

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

    private static Path defaultRecoveryFile() {
        Path base = ApplicationSettings.defaultConfigDirectory(
                System.getProperty("os.name", ""), System.getProperty("user.home", ""),
                System.getenv("XDG_CONFIG_HOME"), System.getenv("APPDATA"));
        return base.resolve(AUTOSAVE_FILE_NAME);
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
        pending = java.util.concurrent.CompletableFuture.runAsync(() -> {
            try { Files.deleteIfExists(recoveryFile); }
            catch (IOException failure) { LOG.log(java.util.logging.Level.WARNING, "Could not delete recovery file", failure); }
        }, worker);
    }

    /**
     * Speichert den aktuellen Markdown-Inhalt in die Wiederherstellungsdatei,
     * falls das Dokument modifiziert ist und nicht leer. Wird periodisch vom
     * Autosave-Timer aufgerufen. I/O-Fehler werden unterdrückt, damit der
     * Editor nie blockiert wird.
     */
    private void saveIfModified() {
        if (saving || !editor.isModified() || !editor.getControl().isEditable()) return;
        var snapshot = editor.getDocumentSnapshot();
        if (snapshot.getText().isBlank()) return;
        var directory = editor.getCurrentFile().map(file -> file.toPath().toAbsolutePath().getParent()).orElse(null);
        saving = true;
        pending = java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                new MarkdownFileHandler().write(recoveryFile.toFile(),
                        new de.richmarkdown.markdown.MarkdownWriter().write(snapshot, directory));
            } catch (IOException failure) { LOG.log(java.util.logging.Level.WARNING, "Could not write recovery file", failure); }
        }, worker);
        pending.whenComplete((ignored, failure) -> javafx.application.Platform.runLater(() -> saving = false));
    }

    /** Stops timer and drains already ordered recovery writes/deletions. */
    @Override public void close() { timer.stop(); worker.shutdown(); }
}
