package de.richmarkdown.ui.dialogs;

import de.richmarkdown.FxTestUtil;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CodeAssistantDialogTest {
    @BeforeAll static void fx() throws Exception { FxTestUtil.startToolkit(); }
    @Test void dialogEmitsStandardCodeFenceAndCancelReturnsNothing() {
        FxTestUtil.runAndWait(() -> {
            Stage owner = new Stage(); owner.setScene(new Scene(new StackPane()));
            var dialog = new CodeAssistantDialog(owner, "return 1;");
            var language = (ComboBox<?>) dialog.getDialogPane().lookup("#code-language");
            language.getEditor().setText("java");
            assertEquals("```java\nreturn 1;\n```", dialog.getResultConverter().call(ButtonType.OK));
            assertNull(dialog.getResultConverter().call(ButtonType.CANCEL));
            assertEquals("return 1;", ((TextArea) dialog.getDialogPane().lookup("#code-content")).getText());
            for (var type : dialog.getDialogPane().getButtonTypes()) assertNotNull(((Button) dialog.getDialogPane().lookupButton(type)).getTooltip());
        });
    }
}
