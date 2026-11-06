package de.richmarkdown.ui;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.features.editing.EditingCommands;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.io.WorkspaceSession;
import de.richmarkdown.settings.ApplicationSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.event.ActionEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceWindowTest {
    @TempDir Path directory;
    private WorkspaceWindow workspace;
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @BeforeEach void setup() {
        I18n.setSettingsStore(ApplicationSettings.inMemory());
        workspace = FxTestUtil.callAndWait(() -> new WorkspaceWindow(new WorkspaceSession(directory.resolve("session.properties"))));
        FxTestUtil.runAndWait(workspace::show);
    }
    @AfterEach void close() { FxTestUtil.runAndWait(workspace::dispose); }
    private void flush() { for (int i = 0; i < 4; i++) FxTestUtil.flushFxEvents(); }
    @Test void tabsKeepIndependentUndoSelectionAndReadOnlyState() {
        EditorWindow first = FxTestUtil.callAndWait(workspace::newDocument); flush();
        FxTestUtil.runAndWait(() -> first.getEditor().setMarkdown("first")); flush();
        FxTestUtil.runAndWait(() -> new EditingCommands(first.getEditor()).paste("A", false)); flush();
        EditorWindow second = FxTestUtil.callAndWait(workspace::newDocument); flush();
        FxTestUtil.runAndWait(() -> second.getEditor().setMarkdown("second")); flush();
        FxTestUtil.runAndWait(() -> {
            second.setReadOnly(true); workspace.selectRelative(-1);
            assertSame(first, workspace.activeDocument()); assertFalse(first.isReadOnly());
            first.getEditorControl().getActionFactory().undo().execute(new ActionEvent());
        }); flush();
        FxTestUtil.runAndWait(() -> {
            assertEquals("first", first.getEditor().getDocumentSnapshot().getText());
            assertEquals("second", second.getEditor().getDocumentSnapshot().getText());
            workspace.selectRelative(1); assertSame(second, workspace.activeDocument()); assertTrue(second.isReadOnly());
        });
    }
    @Test void cancelledClosePreservesDirtyTabAndWorkspace() {
        EditorWindow editor = FxTestUtil.callAndWait(workspace::newDocument); flush();
        FxTestUtil.runAndWait(() -> new EditingCommands(editor.getEditor()).paste("unsaved", false)); flush();
        FxTestUtil.runAndWait(() -> {
            editor.setConfirmationHandler((title, message) -> false);
            assertFalse(workspace.closeActive()); assertEquals(1, workspace.documentCount());
            assertFalse(workspace.closeWorkspace()); assertSame(editor, workspace.activeDocument());
            assertEquals("unsaved", editor.getEditor().getDocumentSnapshot().getText());
            editor.setConfirmationHandler((title, message) -> true);
            assertTrue(workspace.closeActive()); assertEquals(1, workspace.documentCount());
            assertNotSame(editor, workspace.activeDocument());
        });
    }
    @Test void fileTabsDeduplicateRestoreSessionAndReopenClosed() throws Exception {
        Path first = directory.resolve("first.md"); Path second = directory.resolve("second.md");
        Files.writeString(first, "first"); Files.writeString(second, "second");
        EditorWindow original = FxTestUtil.callAndWait(() -> workspace.openFile(first.toFile())); flush();
        FxTestUtil.runAndWait(() -> assertSame(original, workspace.openFile(first.toFile()))); flush();
        EditorWindow other = FxTestUtil.callAndWait(() -> workspace.openFile(second.toFile())); flush();
        FxTestUtil.runAndWait(() -> {
            assertEquals(2, workspace.documentCount()); other.setReadOnly(true);
            assertEquals(2, workspace.captureSession().entries().size());
            assertTrue(workspace.closeActive()); assertEquals(1, workspace.documentCount());
            workspace.reopenClosed(); assertEquals(2, workspace.documentCount());
            workspace.activeDocument().setReadOnly(true);
            assertTrue(workspace.closeWorkspace());
        });
        WorkspaceWindow restored = FxTestUtil.callAndWait(() -> new WorkspaceWindow(new WorkspaceSession(directory.resolve("session.properties"))));
        try {
            FxTestUtil.runAndWait(() -> { restored.show(); restored.restoreSession(); }); flush();
            FxTestUtil.runAndWait(() -> {
                assertEquals(2, restored.documentCount());
                assertEquals(second.toFile(), restored.activeDocument().getCurrentFile());
                assertTrue(restored.activeDocument().isReadOnly());
            });
        } finally { FxTestUtil.runAndWait(restored::dispose); }
    }
    @Test void splitViewReusesDocumentsAndReturnsThemToTabs() {
        EditorWindow first = FxTestUtil.callAndWait(workspace::newDocument); flush();
        EditorWindow second = FxTestUtil.callAndWait(workspace::newDocument); flush();
        FxTestUtil.runAndWait(() -> {
            assertTrue(workspace.showSideBySide()); assertTrue(workspace.isSideBySide());
            assertEquals(2, workspace.documentCount());
            assertFalse(first.getEditor().isModified()); assertFalse(second.getEditor().isModified());
            workspace.closeSideBySide(); assertFalse(workspace.isSideBySide());
            workspace.showSideBySide(); workspace.selectRelative(-1);
            assertFalse(workspace.isSideBySide()); assertSame(first, workspace.activeDocument());
            assertTrue(workspace.closeActive()); assertEquals(1, workspace.documentCount());
            assertFalse(workspace.showSideBySide());
        });
    }

    @Test void startupRestoresFilesBeforeTheHostIsShown() throws Exception {
        Path first = directory.resolve("startup.md"); Files.writeString(first, "# Startup\n\nbody");
        var session = new WorkspaceSession(directory.resolve("startup.properties"));
        session.save(new WorkspaceSession.State(java.util.List.of(new WorkspaceSession.Entry(first.toString(), 0, 0, false)), 0));
        WorkspaceWindow hidden = FxTestUtil.callAndWait(() -> new WorkspaceWindow(session));
        try {
            FxTestUtil.runAndWait(hidden::restoreSession); flush();
            FxTestUtil.runAndWait(() -> {
                assertEquals(1, hidden.documentCount());
                assertEquals(1, hidden.captureSession().entries().size());
                assertTrue(hidden.activeDocument().getEditor().getMarkdownSource().contains("Startup"));
            });
        } finally { FxTestUtil.runAndWait(hidden::dispose); }
    }

    @Test void restoreKeepsTheActiveFileWhenEarlierFilesWereDeleted() throws Exception {
        Path active = directory.resolve("active.md"), later = directory.resolve("later.md");
        Files.writeString(active, "active"); Files.writeString(later, "later");
        var session = new WorkspaceSession(directory.resolve("filtered-session.properties"));
        session.save(new WorkspaceSession.State(java.util.List.of(
                new WorkspaceSession.Entry(directory.resolve("deleted.md").toString(), 0, 0, false),
                new WorkspaceSession.Entry(active.toString(), 0, 0, true),
                new WorkspaceSession.Entry(later.toString(), 0, 0, false)), 1));
        WorkspaceWindow restored = FxTestUtil.callAndWait(() -> new WorkspaceWindow(session));
        try {
            FxTestUtil.runAndWait(() -> { restored.show(); restored.restoreSession(); }); flush();
            FxTestUtil.runAndWait(() -> {
                assertEquals(2, restored.documentCount());
                assertEquals(active.toFile(), restored.activeDocument().getCurrentFile());
                assertTrue(restored.activeDocument().isReadOnly());
                assertEquals(0, restored.captureSession().active());
            });
        } finally { FxTestUtil.runAndWait(() -> { restored.dispose(); restored.close(); }); }
    }

}
