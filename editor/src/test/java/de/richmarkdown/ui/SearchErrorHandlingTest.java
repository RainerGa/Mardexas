package de.richmarkdown.ui;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.findreplace.FindReplaceEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SearchErrorHandlingTest {
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @Test void repeatSearchReportsExhaustedRegexBudgetWithoutThrowing() {
        EditorWindow window = FxTestUtil.callAndWait(() -> { var w = new EditorWindow(); w.show(); return w; });
        try {
            FxTestUtil.runAndWait(() -> window.getEditor().setMarkdown("a".repeat(500)));
            FxTestUtil.flushFxEvents();
            FxTestUtil.runAndWait(() -> {
                var options = new FindReplaceEngine.FindOptions(); options.setRegex(true);
                window.rememberFindCriteria("a+a+a+a+a+z", options);
                assertDoesNotThrow(window::findNextWithoutDialog);
                assertNotNull(window.getLastError());
            });
        } finally {
            FxTestUtil.runAndWait(() -> {
                for (var stage : java.util.List.copyOf(javafx.stage.Window.getWindows())) stage.hide();
                window.disposeFeatures();
            });
        }
    }
}
