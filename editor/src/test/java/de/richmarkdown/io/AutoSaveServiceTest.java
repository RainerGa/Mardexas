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

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests f�r den AutoSave-Dienst (AddFeatures #15).
 * Pr�ft Wiederherstellungsdatei, Speichern bei Modifikation und Wiederherstellung.
 */
class AutoSaveServiceTest {

    private MarkdownEditor editor;
    private AutoSaveService autoSave;
    private Path recoveryFile;
    private String originalOsName;
    private String originalUserHome;
    private Stage stage;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    @BeforeEach
    void setUp() throws Exception {
        // System-Properties überschreiben, um Test-Verzeichnis als Config-Dir zu nutzen
        originalOsName = System.getProperty("os.name");
        originalUserHome = System.getProperty("user.home");
        System.setProperty("os.name", "Linux");
        System.setProperty("user.home", tempDir.toString());

        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor();
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(editor.getControl()), 800, 600));
            stage.show();
            autoSave = new AutoSaveService(editor, 100); // kurzes Intervall für schnelle Tests
            recoveryFile = tempDir.resolve(".config").resolve("autosave.md");
        });
    }

    @AfterEach
    void tearDown() {
        // System-Properties wiederherstellen
        if (originalOsName != null) {
            System.setProperty("os.name", originalOsName);
        } else {
            System.clearProperty("os.name");
        }
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        } else {
            System.clearProperty("user.home");
        }
    }

    private void invokeSaveIfModified() throws Exception {
        Method method = AutoSaveService.class.getDeclaredMethod("saveIfModified");
        method.setAccessible(true);
        FxTestUtil.runAndWait(() -> {
            try {
                method.invoke(autoSave);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void setModifiedContent(String text) {
        FxTestUtil.runAndWait(() -> {
            editor.getControl().getActionFactory().insertText(text).execute(new ActionEvent());
        });
        FxTestUtil.flushFxEvents();
    }

    @Test
    void hasRecoveryFileIsFalseInitially() {
        assertFalse(FxTestUtil.callAndWait(autoSave::hasRecoveryFile),
                "keine Wiederherstellungsdatei vor dem ersten Speichern");
    }

    @Test
    void recoverReturnsFalseWhenNoFileExists() {
        assertFalse(FxTestUtil.callAndWait(autoSave::recover),
                "recover() liefert false, wenn keine Datei existiert");
    }

    @Test
    void deleteRecoveryFileDoesNotThrowWhenAbsent() {
        // darf keine Ausnahme werfen, wenn keine Datei vorhanden ist
        assertDoesNotThrow(() -> FxTestUtil.runAndWait(autoSave::deleteRecoveryFile));
    }

    @Test
    void saveIfModifiedCreatesRecoveryFile() throws Exception {
        setModifiedContent("Testinhalt");
        assertTrue(FxTestUtil.callAndWait(editor::isModified), "Editor ist nach Einfügen modifiziert");
        invokeSaveIfModified();

        assertTrue(FxTestUtil.callAndWait(autoSave::hasRecoveryFile),
                "Wiederherstellungsdatei wurde nach Modifikation erstellt");
        assertTrue(FxTestUtil.callAndWait(() -> java.nio.file.Files.exists(recoveryFile)),
                "Datei existiert auf dem Dateisystem");
    }

    @Test
    void recoverRestoresContentAndDeletesFile() throws Exception {
        setModifiedContent("Wiederherstellungstext");
        invokeSaveIfModified();

        assertTrue(FxTestUtil.callAndWait(autoSave::hasRecoveryFile));

        // Neuen Inhalt setzen, dann wiederherstellen
        setModifiedContent("Neuer Inhalt");
        boolean recovered = FxTestUtil.callAndWait(autoSave::recover);
        FxTestUtil.flushFxEvents();

        assertTrue(recovered, "recover() liefert true bei erfolgreicher Wiederherstellung");
        assertEquals("Wiederherstellungstext", FxTestUtil.callAndWait(editor::getMarkdown),
                "Editor-Inhalt wurde aus Wiederherstellungsdatei wiederhergestellt");
        assertFalse(FxTestUtil.callAndWait(autoSave::hasRecoveryFile),
                "Wiederherstellungsdatei wurde nach recover() gelöscht");
    }

    @Test
    void noRecoveryFileWhenNotModified() throws Exception {
        // Editor ist nicht modifiziert, kein Autosave
        invokeSaveIfModified();
        assertFalse(FxTestUtil.callAndWait(autoSave::hasRecoveryFile),
                "keine Wiederherstellungsdatei bei unverändertem Dokument");
    }

    @Test
    void startAndStopDoNotThrow() {
        assertDoesNotThrow(() -> FxTestUtil.runAndWait(autoSave::start));
        assertDoesNotThrow(() -> FxTestUtil.runAndWait(autoSave::stop));
    }
}
