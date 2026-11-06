package de.richmarkdown.ui;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.settings.ApplicationSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccessibilityOptionsTest {
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @Test void displayOptionsDoNotModifyMarkdownOrDirtyState() {
        FxTestUtil.runAndWait(() -> {
            I18n.setSettingsStore(ApplicationSettings.inMemory());
            EditorWindow window = new EditorWindow();
            try {
                window.getScene().getRoot().applyCss();
                var access = new AccessibilityOptions(window);
                String markdown = window.getEditor().getMarkdownSource();
                boolean dirty = window.getEditor().isModified();
                access.apply(true, false, 17); assertTrue(window.getEditorRoot().getStyle().contains("17px"));
                access.apply(false, true, 20); assertTrue(window.getEditorRoot().getStyle().contains("yellow"));
                access.apply(false, false, 13); assertFalse(window.getEditorRoot().getStyle().contains("black"));
                assertEquals(markdown, window.getEditor().getMarkdownSource()); assertEquals(dirty, window.getEditor().isModified());
                assertThrows(IllegalArgumentException.class, () -> access.apply(false,false,99));
            } finally { window.disposeFeatures(); window.close(); I18n.resetSettingsStore(); }
        });
    }
}
