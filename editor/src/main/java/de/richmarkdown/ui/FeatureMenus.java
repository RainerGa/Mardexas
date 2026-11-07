package de.richmarkdown.ui;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.Selection;
import de.richmarkdown.features.editing.EditingCommands;
import de.richmarkdown.features.editing.DocumentEdits;
import de.richmarkdown.features.editing.WritingViewController;
import de.richmarkdown.features.tables.TableTools;
import de.richmarkdown.markdown.MarkdownWriter;
import de.richmarkdown.ui.dialogs.WritingAssistActions;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/** Connects feature services to the existing shell without changing document serialization. */
public final class FeatureMenus {
    private FeatureMenus() { }
    public static void install(EditorWindow window, MenuBar menus) {
        if (menus.getMenus().size() < 8) return;
        CommandIdentity.assign(menus);
        var editor = window.getEditor();
        CommandIdentity.menu(menus, "menu.file").getItems().add(item("file.printLayout", FeatureUi.text("Druckbereich und Gestaltung …", "Print range and layout …"), window::configurePrint));
        Menu edit = EditingCommands.createMenuWithOwner(editor, window::getDialogOwner);
        CommandIdentity.menu(menus, "menu.edit").getItems().add(edit);
        var commands = new EditingCommands(editor);
        Menu writing = WritingAssistActions.createMenuWithOwner(window::getDialogOwner, editor::getMarkdownSource,
                () -> selectedMarkdown(window), value -> commands.paste(value, true),
                value -> commands.paste(value, false), value -> newFromMarkdown(window, value));
        writing.disableProperty().bind(window.readOnlyProperty());
        CommandIdentity.menu(menus, "menu.insert").getItems().add(writing);
        Menu tables = TableTools.createMenu(editor, window::getDialogOwner);
        CommandIdentity.menu(menus, "menu.table").getItems().add(tables);
        WritingViewController views = new WritingViewController(editor);
        javafx.scene.Node[] previousStructure = new javafx.scene.Node[1];
        views.distractionFreeProperty().addListener((observable, oldValue, enabled) -> {
            window.getChrome().setVisible(!enabled); window.getChrome().setManaged(!enabled);
            if (enabled) {
                previousStructure[0] = window.getEditorRoot().getLeft();
                window.getEditorRoot().setLeft(null);
            } else window.getEditorRoot().setLeft(previousStructure[0]);
            window.getStatusBar().setVisible(!enabled); window.getStatusBar().setManaged(!enabled);
        });
        window.getEditorRoot().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && views.distractionFreeProperty().get()) {
                views.distractionFreeProperty().set(false); event.consume();
            }
        });
        CommandIdentity.menu(menus, "menu.view").getItems().add(views.createMenu());
        WritingDashboard dashboard = new WritingDashboard(window);
        CommandIdentity.menu(menus, "menu.extras").getItems().add(item("tools.writingDashboard", FeatureUi.text("Schreibstatistik und Ziele …", "Writing statistics and goals …"), dashboard::show));
        AccessibilityOptions access = new AccessibilityOptions(window);
        CommandIdentity.menu(menus, "menu.extras").getItems().add(item("tools.accessibility", FeatureUi.text("Lesbarkeit und Bedienung …", "Readability and accessibility …"), access::show));
        Menu tabs = new Menu(FeatureUi.text("Dokument-Tabs", "Document tabs"));
        tabs.setId("menu.documentTabs");
        tabs.getItems().addAll(
                item("tabs.new", FeatureUi.text("Neuer Tab", "New tab"), window::newDocument),
                item("tabs.next", FeatureUi.text("Nächster Tab", "Next tab"), () -> { if (window.getWorkspace() != null) window.getWorkspace().selectRelative(1); }),
                item("tabs.previous", FeatureUi.text("Vorheriger Tab", "Previous tab"), () -> { if (window.getWorkspace() != null) window.getWorkspace().selectRelative(-1); }),
                item("tabs.sideBySide", FeatureUi.text("Zwei Dokumente nebeneinander", "Two documents side by side"), () -> { if (window.getWorkspace() != null) window.getWorkspace().showSideBySide(); }),
                item("tabs.closeSideView", FeatureUi.text("Geteilte Ansicht schließen", "Close side view"), () -> { if (window.getWorkspace() != null) window.getWorkspace().closeSideBySide(); }),
                item("tabs.reopenClosed", FeatureUi.text("Geschlossenen Tab öffnen", "Reopen closed tab"), () -> { if (window.getWorkspace() != null) window.getWorkspace().reopenClosed(); }),
                item("tabs.close", FeatureUi.text("Tab schließen", "Close tab"), () -> { if (window.getWorkspace() != null) window.getWorkspace().closeActive(); else window.exit(); }));
        CommandIdentity.menu(menus, "menu.window").getItems().add(tabs);
        var speech = new de.richmarkdown.io.SpeechReader(failure -> javafx.application.Platform.runLater(() -> FeatureUi.error(window.getDialogOwner(), failure)));
        window.addFeatureDisposer(speech::close);
        Menu reading = new Menu(FeatureUi.text("Vorlesen", "Read aloud"));
        reading.setId("menu.speech");
        reading.getItems().addAll(item("speech.read", FeatureUi.text("Auswahl oder Dokument vorlesen", "Read selection or document"), () -> {
            try {
                var selection = window.getEditorControl().getSelection();
                var snapshot = editor.getDocumentSnapshot();
                String text = snapshot.getText();
                if (selection != null && selection.isDefined()) {
                    var buffer = ((RichTextAreaSkin) window.getEditorControl().getSkin()).getViewModel().getTextBuffer();
                    text = text.substring(buffer.getText(0, selection.getStart()).length(), buffer.getText(0, selection.getEnd()).length());
                }
                speech.speak(text);
            } catch (java.io.IOException failure) {
                FeatureUi.error(window.getDialogOwner(), new java.io.IOException(FeatureUi.text(
                        "Die Sprachausgabe konnte nicht gestartet werden. Linux benötigt espeak; macOS und Windows verwenden ihre Systemstimme.",
                        "Could not start speech. Linux requires espeak; macOS and Windows use the system voice."), failure));
            } catch (RuntimeException failure) { FeatureUi.error(window.getDialogOwner(), failure); }
        }), item("speech.stop", FeatureUi.text("Vorlesen stoppen", "Stop reading"), speech::stop));
        CommandIdentity.menu(menus, "menu.extras").getItems().add(reading);
        CommandIdentity.menu(menus, "menu.extras").getItems().add(item("search.folder", FeatureUi.text("Ordner durchsuchen und ersetzen …", "Search and replace in folder …"),
                () -> new de.richmarkdown.ui.dialogs.FolderSearchDialog(window.getDialogOwner(), path -> window.openFile(path.toFile())).show()));
        var codeAssistant = item("insert.codeAssistant", FeatureUi.text("Code-Assistent …", "Code assistant …"), () -> {
            String initial = "";
            var selection = editor.getControl().getSelection();
            if (selection != null && selection.isDefined()) {
                var buffer = ((RichTextAreaSkin) editor.getControl().getSkin()).getViewModel().getTextBuffer();
                initial = buffer.getText(selection.getStart(), selection.getEnd());
            }
            new de.richmarkdown.ui.dialogs.CodeAssistantDialog(window.getDialogOwner(), initial).showAndWait().ifPresent(value -> commands.paste(value, true));
        });
        codeAssistant.disableProperty().bind(window.readOnlyProperty());
        CommandIdentity.menu(menus, "menu.insert").getItems().add(codeAssistant);
        CommandIdentity.menu(menus, "menu.extras").getItems().add(de.richmarkdown.features.checking.CheckingTools.createMenu(editor, window::getDialogOwner));
        javafx.scene.control.Label issues = new javafx.scene.control.Label();
        issues.setTooltip(new javafx.scene.control.Tooltip(FeatureUi.text("Markdown-Prüfung öffnen", "Open Markdown checks")));
        issues.setOnMouseClicked(event -> window.showIssueCheckDialog());
        window.getStatusBar().getChildren().add(issues);
        var live = new de.richmarkdown.features.checking.LiveMarkdownCheck(editor::getMarkdownSource,
                () -> de.richmarkdown.features.checking.CheckingTools.directory(editor), result -> issues.setText(result.isEmpty() ? "" : "⚠ " + result.size()));
        Runnable changed = live::request;
        editor.addChangeListener(changed);
        javafx.beans.value.ChangeListener<Number> lengthChanged = (observable, oldValue, value) -> live.request();
        editor.getControl().textLengthProperty().addListener(lengthChanged);
        window.addFeatureDisposer(() -> { editor.removeChangeListener(changed); editor.getControl().textLengthProperty().removeListener(lengthChanged); live.close(); });
        CommandIdentity.assign(menus);
    }
    private static javafx.scene.control.MenuItem item(String id, String label, Runnable action) {
        var item = FeatureUi.item(label, action);
        item.setId(id);
        return item;
    }
    /** Retained for existing callers; now assigns semantic, language-independent command IDs. */
    static void assignMissingCommandIds(MenuBar menus) {
        CommandIdentity.assign(menus);
    }
    public static String selectedMarkdown(EditorWindow window) {
        Selection selection = window.getEditorControl().getSelection();
        if (selection == null || !selection.isDefined()) return "";
        var skin = (RichTextAreaSkin) window.getEditorControl().getSkin();
        var buffer = skin.getViewModel().getTextBuffer();
        var document = DocumentEdits.slice(window.getEditor().getDocumentSnapshot(),
                buffer.getText(0, selection.getStart()).length(), buffer.getText(0, selection.getEnd()).length());
        return new MarkdownWriter().write(document,
                window.getEditor().getCurrentFile().map(file -> file.toPath().toAbsolutePath().getParent()).orElse(null));
    }
    public static void newFromMarkdown(EditorWindow window, String markdown) {
        if (window.isReadOnly()) return;
        if (window.getWorkspace() != null) {
            window.getWorkspace().newDocument().getEditor().setMarkdown(markdown);
        } else if (window.mayCloseDocument()) {
            window.newDocument(); window.getEditor().setMarkdown(markdown);
        }
    }
}
