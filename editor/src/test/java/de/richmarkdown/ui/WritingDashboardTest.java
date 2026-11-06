package de.richmarkdown.ui;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.markdown.MarkdownParser;
import de.richmarkdown.settings.ApplicationSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WritingDashboardTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path profile;
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @Test void scopeUsesExportedCoordinatesAndIncludesNestedChapter() {
        var doc = new MarkdownParser().parseInto("# Parent\n\nOne 😀\n\n## Child\n\nTwo\n\n# Next\n\nEnd", null);
        String text = doc.getText();
        int parent = text.indexOf("Parent"), child = text.indexOf("Child"), next = text.indexOf("Next");
        assertEquals(new WritingDashboard.Range(parent,next), WritingDashboard.range(doc,WritingDashboard.Scope.CHAPTER,0,0,parent));
        assertEquals(new WritingDashboard.Range(child,next), WritingDashboard.range(doc,WritingDashboard.Scope.CHAPTER,0,0,child));
        var selected = WritingDashboard.range(doc,WritingDashboard.Scope.SELECTION,text.indexOf("😀"),text.indexOf("😀")+2,0);
        assertEquals("😀",WritingDashboard.scopedText(doc,selected.start(),selected.end()));
        assertThrows(IllegalArgumentException.class,()-> WritingDashboard.range(doc,WritingDashboard.Scope.SELECTION,4,2,0));
        assertThrows(IllegalArgumentException.class,()-> WritingDashboard.scopedText(doc,0,text.length()+1));
    }
    @Test void goalsResetPersistAndDoNotChangeDocument() {
        FxTestUtil.runAndWait(() -> {
            I18n.setSettingsStore(new ApplicationSettings(profile));
            EditorWindow window = new EditorWindow();
            try {
                window.getScene().getRoot().applyCss();
                var dashboard = new WritingDashboard(window);
                String before = window.getEditor().getMarkdownSource();
                boolean modified = window.getEditor().isModified();
                dashboard.reset(300);
                assertEquals(300,dashboard.goal().target()); assertEquals(0,dashboard.goal().addedWords());
                assertEquals("300",I18n.preference("goal.target",""));
                assertThrows(IllegalArgumentException.class,()-> dashboard.reset(0));
                dashboard.show(); dashboard.show();
                assertEquals(before,window.getEditor().getMarkdownSource());
                assertEquals(modified,window.getEditor().isModified());
            } finally { window.disposeFeatures(); window.close(); I18n.resetSettingsStore(); }
        });
    }
    @Test void caretAfterEmojiDoesNotAdvanceIntoFollowingChapter() {
        EditorWindow window = FxTestUtil.callAndWait(() -> {
            EditorWindow created = new EditorWindow();
            created.getScene().getRoot().applyCss();
            created.getEditor().setMarkdown("# First\n\n😀\n\n# Second\n\nbody");
            return created;
        });
        for (int i = 0; i < 4; i++) FxTestUtil.flushFxEvents();
        try {
            FxTestUtil.runAndWait(() -> {
                var snapshot = window.getEditor().getDocumentSnapshot();
                int next = snapshot.getText().indexOf("Second");
                assertTrue(next > 0);
                var buffer = ((com.gluonhq.richtextarea.RichTextAreaSkin) window.getEditorControl().getSkin()).getViewModel().getTextBuffer();
                window.getEditor().goToPosition(buffer.getInternalPosition(next - 1));
                assertEquals(next - 1, window.getEditorControl().getCaretPosition());
                assertEquals(new WritingDashboard.Range(0, next),
                        new WritingDashboard(window).currentRange(snapshot, WritingDashboard.Scope.CHAPTER));
            });
        } finally { FxTestUtil.runAndWait(() -> { window.disposeFeatures(); window.close(); }); }
    }
    @Test void invalidSavedUiSizesUseSafeBounds() {
        I18n.setSettingsStore(new ApplicationSettings(profile));
        try {
            I18n.setPreference("access.size","broken"); assertEquals(13,AccessibilityOptions.savedSize());
            I18n.setPreference("access.size","999"); assertEquals(32,AccessibilityOptions.savedSize());
            I18n.setPreference("access.size","0"); assertEquals(10,AccessibilityOptions.savedSize());
        } finally { I18n.resetSettingsStore(); }
    }
    @Test void visibleStatisticsFollowSelectionChangesWithoutEditingText() throws Exception {
        I18n.setSettingsStore(ApplicationSettings.inMemory());
        EditorWindow window = FxTestUtil.callAndWait(() -> {
            var created = new EditorWindow(); created.show(); created.getEditor().setMarkdown("one two three"); return created;
        });
        for (int i = 0; i < 4; i++) FxTestUtil.flushFxEvents();
        try {
            javafx.scene.control.DialogPane pane = FxTestUtil.callAndWait(() -> {
                new WritingDashboard(window).show();
                var shown = javafx.stage.Window.getWindows().stream().filter(candidate -> candidate != window
                        && candidate.getScene().lookup("#writing-statistics-summary") != null).findFirst().orElseThrow();
                return (javafx.scene.control.DialogPane) shown.getScene().getRoot();
            });
            FxTestUtil.runAndWait(() -> {
                var model = ((com.gluonhq.richtextarea.RichTextAreaSkin) window.getEditorControl().getSkin()).getViewModel();
                model.setSelection(new com.gluonhq.richtextarea.Selection(0, 3));
                ((javafx.scene.control.ComboBox<?>) pane.lookup("#writing-statistics-scope")).getSelectionModel().select(1);
            });
            awaitStatistic(pane, "1");
            FxTestUtil.runAndWait(() -> {
                var model = ((com.gluonhq.richtextarea.RichTextAreaSkin) window.getEditorControl().getSkin()).getViewModel();
                model.setSelection(new com.gluonhq.richtextarea.Selection(0, 7));
            });
            awaitStatistic(pane, "2");
            assertEquals("one two three", FxTestUtil.callAndWait(() -> window.getEditor().getDocumentSnapshot().getText()));
        } finally { FxTestUtil.runAndWait(() -> { window.disposeFeatures(); window.close(); }); }
    }
    private static void awaitStatistic(javafx.scene.control.DialogPane pane, String words) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(6);
        String text;
        do {
            text = FxTestUtil.callAndWait(() -> ((javafx.scene.control.Label) pane.lookup("#writing-statistics-summary")).getText());
            if (text.startsWith("Wörter: " + words + " ·") || text.startsWith("Words: " + words + " ·")) return;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        fail("Expected " + words + " words in visible statistics; got " + text);
    }

}
