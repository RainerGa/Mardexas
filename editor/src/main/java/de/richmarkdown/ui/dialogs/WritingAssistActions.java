package de.richmarkdown.ui.dialogs;

import de.richmarkdown.io.InsertionContent;
import de.richmarkdown.io.MarkdownLibraryStore;
import de.richmarkdown.i18n.I18n;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Window;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Integration boundary: callers supply undoable insertion and guarded new-document operations. */
public final class WritingAssistActions {
    private WritingAssistActions() { }

    public static Menu createMenu(Window owner, Supplier<String> currentMarkdown, Supplier<String> selectedMarkdown,
                                  Consumer<String> insertMarkdown, Consumer<String> newDocument) {
        return createMenu(owner, currentMarkdown, selectedMarkdown, insertMarkdown, insertMarkdown, newDocument);
    }

    public static Menu createMenu(Window owner, Supplier<String> currentMarkdown, Supplier<String> selectedMarkdown,
                                  Consumer<String> insertMarkdown, Consumer<String> insertLiteral,
                                  Consumer<String> newDocument) {
        return createMenuWithOwner(() -> owner, currentMarkdown, selectedMarkdown, insertMarkdown, insertLiteral, newDocument);
    }

    /** Owner is evaluated when a command is executed, after embedding in a host scene. */
    public static Menu createMenuWithOwner(Supplier<Window> owner, Supplier<String> currentMarkdown, Supplier<String> selectedMarkdown,
                                  Consumer<String> insertMarkdown, Consumer<String> insertLiteral,
                                  Consumer<String> newDocument) {
        Menu menu = new Menu(I18n.text("menu.writingAssist"));
        menu.setId("menu.writingAssist");
        menu.getItems().add(item("insert.manageSnippets", () -> library(owner.get(),
                MarkdownLibraryStore.Kind.SNIPPET, "", insertMarkdown)));
        menu.getItems().add(item("insert.saveSnippet", () -> library(owner.get(),
                MarkdownLibraryStore.Kind.SNIPPET, selectedMarkdown.get(), insertMarkdown)));
        menu.getItems().add(item("insert.insertShortcut", () -> shortcut(owner.get(), insertMarkdown)));
        menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().add(item("insert.manageTemplates", () -> library(owner.get(),
                MarkdownLibraryStore.Kind.TEMPLATE, "", newDocument)));
        menu.getItems().add(item("insert.saveTemplate", () -> library(owner.get(),
                MarkdownLibraryStore.Kind.TEMPLATE, currentMarkdown.get(), newDocument)));
        menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().add(item("insert.dateTime", () -> dateTime(owner.get(), insertLiteral)));
        menu.getItems().add(item("insert.specialCharacter", () -> specialCharacter(owner.get(), insertLiteral)));
        return menu;
    }

    private static void library(Window owner, MarkdownLibraryStore.Kind kind, String initial, Consumer<String> target) {
        new WritingLibraryDialog(owner, MarkdownLibraryStore.userLibrary(kind), kind, initial).showAndWait()
                .flatMap(markdown -> TemplateFieldsDialog.resolve(owner, markdown)).ifPresent(target);
    }

    private static void shortcut(Window owner, Consumer<String> insert) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(owner);
        dialog.setTitle(I18n.text("dialog.shortcut.title"));
        dialog.setHeaderText(I18n.text("dialog.shortcut.header"));
        DialogButtons.applyDialogStyles(dialog.getDialogPane());
        dialog.showAndWait().ifPresent(shortcut -> {
            if (shortcut.isBlank()) return;
            Task<List<MarkdownLibraryStore.Entry>> task = new Task<>() {
                @Override protected List<MarkdownLibraryStore.Entry> call() throws Exception {
                    return MarkdownLibraryStore.userLibrary(MarkdownLibraryStore.Kind.SNIPPET).list();
                }
            };
            task.setOnSucceeded(event -> task.getValue().stream()
                    .filter(entry -> entry.shortcut().equals(shortcut.strip())).findFirst()
                    .ifPresentOrElse(entry -> TemplateFieldsDialog.resolve(owner, entry.content()).ifPresent(insert),
                            () -> error(owner, I18n.text("dialog.shortcut.noSnippet"))));
            task.setOnFailed(event -> error(owner, task.getException().getMessage()));
            Thread.ofVirtual().name("snippet-lookup").start(task);
        });
    }

    private static void dateTime(Window owner, Consumer<String> insert) {
        Clock clock = Clock.systemDefaultZone();
        Locale locale = Locale.getDefault();
        List<String> values = List.of(InsertionContent.date(clock, locale, false),
                InsertionContent.date(clock, locale, true), InsertionContent.time(clock, locale, false),
                InsertionContent.time(clock, locale, true));
        ChoiceDialog<String> dialog = new ChoiceDialog<>(values.getFirst(), values);
        dialog.initOwner(owner);
        dialog.setTitle(I18n.text("dialog.dateTime.title"));
        dialog.setHeaderText(I18n.text("dialog.dateTime.header"));
        DialogButtons.applyDialogStyles(dialog.getDialogPane());
        dialog.showAndWait().ifPresent(insert);
    }

    private static void specialCharacter(Window owner, Consumer<String> insert) {
        TextInputDialog dialog = new TextInputDialog("U+00A9");
        dialog.initOwner(owner);
        dialog.setTitle(I18n.text("dialog.specialCharacter.title"));
        dialog.setHeaderText(I18n.text("dialog.specialCharacter.header"));
        DialogButtons.applyDialogStyles(dialog.getDialogPane());
        dialog.showAndWait().ifPresent(value -> {
            try { insert.accept(InsertionContent.codePoint(value)); }
            catch (IllegalArgumentException error) { error(owner, error.getMessage()); }
        });
    }

    private static MenuItem item(String key, Runnable action) {
        MenuItem item = new MenuItem(I18n.text(key));
        item.setId(key);
        item.setOnAction(event -> action.run());
        return item;
    }

    private static void error(Window owner, String text) {
        Alert alert = new Alert(Alert.AlertType.ERROR, text, DialogButtons.OK);
        alert.initOwner(owner);
        alert.setTitle(I18n.text("dialog.insertError.title"));
        alert.setHeaderText(I18n.text("dialog.insertError.header"));
        DialogButtons.applyDialogStyles(alert.getDialogPane());
        alert.showAndWait();
    }
}
