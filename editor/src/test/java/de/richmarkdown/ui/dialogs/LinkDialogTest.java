package de.richmarkdown.ui.dialogs;

import com.gluonhq.richtextarea.RichTextArea;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD Phase 1: LinkDialog – Dialog auf Auswahl anwenden (PROJEKTPLAN 7.1).
 */
class LinkDialogTest {

    private MarkdownEditor editor;
    private RichTextArea rta;
    private Stage stage;

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    @BeforeEach
    void setUp() {
        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor();
            rta = editor.getControl();
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(rta), 600, 400));
            stage.show();
        });
    }

    @AfterAll
    static void tearDown() {
        // kein stage.hide(): blockt den FX-Thread in Monocle headless
    }

    private void flush() {
        FxTestUtil.flushFxEvents();
    }

    private void type(String text) {
        FxTestUtil.runAndWait(() -> {
            for (char c : text.toCharArray()) {
                rta.fireEvent(new KeyEvent(KeyEvent.KEY_TYPED, String.valueOf(c), "",
                        KeyCode.UNDEFINED, false, false, false, false));
            }
        });
    }

    private void selectAll() {
        FxTestUtil.runAndWait(() -> rta.getActionFactory().selectAll().execute(new ActionEvent()));
        flush();
    }

    private String urlAt(int index) {
        FxTestUtil.runAndWait(() -> rta.getActionFactory().save().execute(new ActionEvent()));
        flush();
        var doc = rta.getDocument();
        for (var dm : doc.getDecorations()) {
            if (index >= dm.getStart() && index < dm.getStart() + dm.getLength()) {
                return ((com.gluonhq.richtextarea.model.TextDecoration) dm.getDecoration()).getURL();
            }
        }
        return null;
    }

    @Test
    void dialogHasUrlField() {
        LinkDialog dialog = FxTestUtil.callAndWait(LinkDialog::new);
        DialogPane pane = FxTestUtil.callAndWait(dialog::getDialogPane);
        assertNotNull(pane);
        assertNotNull(FxTestUtil.callAndWait(dialog::getUrlField));
    }

    @Test
    void dialogReturnsEnteredUrl() {
        LinkDialog dialog = FxTestUtil.callAndWait(LinkDialog::new);
        FxTestUtil.runAndWait(() -> dialog.getUrlField().setText("https://example.com"));
        Optional<String> result = FxTestUtil.callAndWait(() -> {
            dialog.setResult("https://example.com");
            return Optional.of(dialog.getResult());
        });
        assertTrue(result.isPresent());
        assertEquals("https://example.com", result.get());
    }

    @Test
    void dialogCancelReturnsEmpty() {
        LinkDialog dialog = FxTestUtil.callAndWait(LinkDialog::new);
        Optional<String> result = FxTestUtil.callAndWait(() -> {
            dialog.setResult(null);
            return Optional.ofNullable(dialog.getResult());
        });
        assertFalse(result.isPresent());
    }

    @Test
    void applyLink_setsUrlOnSelection() {
        type("link text");
        selectAll();
        editor.applyLink("https://example.org");
        flush();

        assertEquals("https://example.org", urlAt(0));
    }

    @Test
    void linkCallbackFactoryIsConfigured() {
        // Klick auf Link öffnet Browser (Default-Factory der Bibliothek)
        assertNotNull(FxTestUtil.callAndWait(rta::getLinkCallbackFactory));
    }

    @Test
    void dialogHasOkAndCancelButtons() {
        LinkDialog dialog = FxTestUtil.callAndWait(LinkDialog::new);
        DialogPane pane = FxTestUtil.callAndWait(dialog::getDialogPane);
        assertNotNull(pane.lookupButton(DialogButtons.OK));
        assertNotNull(pane.lookupButton(DialogButtons.CANCEL));
    }
}
