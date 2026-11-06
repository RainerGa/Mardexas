package de.richmarkdown.features.printing;

import de.richmarkdown.FxTestUtil;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PrintOptionsDialogTest {
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }

    @Test void visibleDialogBlocksMissingSelectionAndInvalidMarginsBeforeClosing() {
        FxTestUtil.runAndWait(() -> {
            var dialog = new PrintOptionsDialog(null, PrintOptions.defaults(), false);
            dialog.show();
            try {
                var pane = dialog.getDialogPane();
                var scope = (ComboBox<?>) pane.lookup(".combo-box");
                var ok = (Button) pane.lookupButton(ButtonType.OK);
                scope.getSelectionModel().select(PrintOptions.Scope.SELECTION.ordinal());
                ok.fire();
                assertTrue(dialog.isShowing()); assertNull(dialog.getResult());
                scope.getSelectionModel().select(PrintOptions.Scope.DOCUMENT.ordinal());
                var fields = pane.lookupAll(".text-field").stream().map(TextField.class::cast).toList();
                var margin = fields.stream().filter(field -> field.getText().equals("20.0")).findFirst().orElseThrow();
                margin.setText("-1"); ok.fire();
                assertTrue(dialog.isShowing()); assertNull(dialog.getResult());
                margin.setText("15,5"); ok.fire();
                assertFalse(dialog.isShowing()); assertNotNull(dialog.getResult());
                assertTrue(dialog.getResult().top() == 15.5 || dialog.getResult().right() == 15.5
                        || dialog.getResult().bottom() == 15.5 || dialog.getResult().left() == 15.5);
            } finally { dialog.close(); }
        });
    }

    @Test void cancelInVisibleDialogNeverCreatesPrintOptions() {
        FxTestUtil.runAndWait(() -> {
            var dialog = new PrintOptionsDialog(null, PrintOptions.defaults(), true);
            dialog.show();
            ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).fire();
            assertFalse(dialog.isShowing()); assertNull(dialog.getResult());
        });
    }
}
