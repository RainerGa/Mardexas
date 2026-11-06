package de.richmarkdown.ui.dialogs;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.io.MarkdownLibraryStore;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Menu;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WritingAssistDialogsTest {
    @TempDir Path root;
    @BeforeAll static void startFx() throws Exception { FxTestUtil.startToolkit(); }

    private static Stage owner() {
        Stage stage = new Stage();
        stage.setScene(new javafx.scene.Scene(new javafx.scene.layout.StackPane()));
        return stage;
    }

    @Test void fieldsDialogProducesOrdinaryMarkdownAndCancelDoesNotInsert() {
        FxTestUtil.runAndWait(() -> {
            TemplateFieldsDialog dialog = new TemplateFieldsDialog(owner(), "# ${Titel}\n${Name}");
            ((TextField) ((javafx.scene.control.ScrollPane) dialog.getDialogPane().getContent()).getContent().lookup("#template-field-0")).setText("Hallo");
            ((TextField) ((javafx.scene.control.ScrollPane) dialog.getDialogPane().getContent()).getContent().lookup("#template-field-1")).setText("Welt");
            assertEquals("# Hallo\nWelt", dialog.getResultConverter().call(ButtonType.OK));
            assertNull(dialog.getResultConverter().call(ButtonType.CANCEL));
            assertEquals("plain", TemplateFieldsDialog.resolve(null, "plain").orElseThrow());
        });
    }

    @Test void libraryDialogPrefillsCurrentMarkdownAndCancelPreservesDisk() throws Exception {
        var store = new MarkdownLibraryStore(root, MarkdownLibraryStore.Kind.TEMPLATE);
        FxTestUtil.runAndWait(() -> {
            WritingLibraryDialog dialog = new WritingLibraryDialog(owner(), store,
                    MarkdownLibraryStore.Kind.TEMPLATE, "# Current\nText");
            assertEquals("# Current\nText", ((TextArea) dialog.getDialogPane().lookup("#library-content")).getText());
            assertNull(dialog.getResultConverter().call(ButtonType.CANCEL));
            var use = dialog.getDialogPane().getButtonTypes().getFirst();
            assertEquals("# Current\nText", dialog.getResultConverter().call(use));
            for (var type : dialog.getDialogPane().getButtonTypes()) {
                assertNotNull(((Button) dialog.getDialogPane().lookupButton(type)).getTooltip());
            }
            TextField shortcut = (TextField) dialog.getDialogPane().lookup("#library-shortcut");
            assertFalse(shortcut.isVisible());
        });
        assertTrue(store.list().isEmpty());
    }

    @Test void snippetShortcutFieldAndMenuActionsAreAvailable() {
        FxTestUtil.runAndWait(() -> {
            WritingLibraryDialog dialog = new WritingLibraryDialog(owner(),
                    new MarkdownLibraryStore(root, MarkdownLibraryStore.Kind.SNIPPET),
                    MarkdownLibraryStore.Kind.SNIPPET, "");
            assertTrue(dialog.getDialogPane().lookup("#library-shortcut").isVisible());
            assertTrue(dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().getFirst()).isDisable());
            AtomicReference<String> insertion = new AtomicReference<>();
            Menu menu = WritingAssistActions.createMenu(null, () -> "current", () -> "selection", insertion::set, insertion::set);
            assertEquals(9, menu.getItems().size());
            assertEquals(7, menu.getItems().stream().filter(item -> item.getOnAction() != null).count());
            assertNull(insertion.get());
        });
    }
    @Test void specialCharacterDialogInsertsMarkdownPunctuationLiterallyAtCaret() {
        var editor = FxTestUtil.callAndWait(de.richmarkdown.editor.MarkdownEditor::new);
        Stage stage = FxTestUtil.callAndWait(() -> {
            Stage created = new Stage();
            created.setScene(new javafx.scene.Scene(new javafx.scene.layout.StackPane(editor.getControl()), 650, 450));
            created.show(); editor.setMarkdown("context"); return created;
        });
        for (int i = 0; i < 4; i++) FxTestUtil.flushFxEvents();
        try {
            FxTestUtil.runAndWait(() -> {
                editor.goToPosition(7);
                var commands = new de.richmarkdown.features.editing.EditingCommands(editor);
                var menu = WritingAssistActions.createMenu(stage, editor::getMarkdownSource, () -> "",
                        value -> commands.paste(value, true), value -> commands.paste(value, false), ignored -> { });
                javafx.application.Platform.runLater(() -> {
                    var window = javafx.stage.Window.getWindows().stream().filter(candidate -> candidate != stage
                            && candidate.getScene().getRoot() instanceof javafx.scene.control.DialogPane).findFirst().orElseThrow();
                    var pane = (javafx.scene.control.DialogPane) window.getScene().getRoot();
                    ((TextField) pane.lookup(".text-field")).setText("U+002A");
                    ((Button) pane.lookupButton(ButtonType.OK)).fire();
                });
                menu.getItems().getLast().fire();
            });
            for (int i = 0; i < 4; i++) FxTestUtil.flushFxEvents();
            assertEquals("context*", FxTestUtil.callAndWait(() -> editor.getDocumentSnapshot().getText()));
            assertEquals(8, FxTestUtil.callAndWait(editor.getControl()::getCaretPosition));
            FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().undo().execute(new javafx.event.ActionEvent()));
            for (int i = 0; i < 3; i++) FxTestUtil.flushFxEvents();
            assertEquals("context", FxTestUtil.callAndWait(() -> editor.getDocumentSnapshot().getText()));
        } finally { FxTestUtil.runAndWait(stage::close); }
    }

}
