package de.richmarkdown.ui.dialogs;

import de.richmarkdown.io.InsertionContent;
import de.richmarkdown.io.MarkdownLibraryStore;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
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
        Menu menu = new Menu("Textbausteine und Vorlagen");
        menu.setId("menu.writingAssist");
        menu.getItems().add(item("insert.manageSnippets", "Textbausteine verwalten / einfügen…", () -> library(owner,
                MarkdownLibraryStore.Kind.SNIPPET, "", insertMarkdown)));
        menu.getItems().add(item("insert.saveSnippet", "Auswahl als Textbaustein speichern…", () -> library(owner,
                MarkdownLibraryStore.Kind.SNIPPET, selectedMarkdown.get(), insertMarkdown)));
        menu.getItems().add(item("insert.insertShortcut", "Textbaustein über Kürzel einfügen…", () -> shortcut(owner, insertMarkdown)));
        menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().add(item("insert.manageTemplates", "Eigene Dokumentvorlagen…", () -> library(owner,
                MarkdownLibraryStore.Kind.TEMPLATE, "", newDocument)));
        menu.getItems().add(item("insert.saveTemplate", "Dokument als Vorlage speichern…", () -> library(owner,
                MarkdownLibraryStore.Kind.TEMPLATE, currentMarkdown.get(), newDocument)));
        menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().add(item("insert.dateTime", "Datum / Uhrzeit einfügen…", () -> dateTime(owner, insertLiteral)));
        menu.getItems().add(item("insert.specialCharacter", "Sonderzeichen einfügen…", () -> specialCharacter(owner, insertLiteral)));
        return menu;
    }

    private static void library(Window owner, MarkdownLibraryStore.Kind kind, String initial, Consumer<String> target) {
        new WritingLibraryDialog(owner, MarkdownLibraryStore.userLibrary(kind), kind, initial).showAndWait()
                .flatMap(markdown -> TemplateFieldsDialog.resolve(owner, markdown)).ifPresent(target);
    }

    private static void shortcut(Window owner, Consumer<String> insert) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(owner);
        dialog.setTitle("Textbaustein-Kürzel");
        dialog.setHeaderText("Kürzel exakt eingeben, zum Beispiel ;adresse");
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
                            () -> error(owner, "Für dieses Kürzel ist kein Textbaustein gespeichert.")));
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
        dialog.setTitle("Datum / Uhrzeit");
        dialog.setHeaderText("Der gewählte Wert wird als gewöhnlicher Text eingefügt.");
        dialog.showAndWait().ifPresent(insert);
    }

    private static void specialCharacter(Window owner, Consumer<String> insert) {
        TextInputDialog dialog = new TextInputDialog("U+00A9");
        dialog.initOwner(owner);
        dialog.setTitle("Sonderzeichen");
        dialog.setHeaderText("Unicode-Codepunkt eingeben, z. B. U+00A9 (©), U+2014 (—), U+1F600 (😀)");
        dialog.showAndWait().ifPresent(value -> {
            try { insert.accept(InsertionContent.codePoint(value)); }
            catch (IllegalArgumentException error) { error(owner, error.getMessage()); }
        });
    }

    private static MenuItem item(String id, String label, Runnable action) {
        MenuItem item = new MenuItem(label);
        item.setId(id);
        item.setOnAction(event -> action.run());
        return item;
    }

    private static void error(Window owner, String text) {
        Alert alert = new Alert(Alert.AlertType.ERROR, text, ButtonType.OK);
        alert.initOwner(owner);
        alert.setHeaderText("Einfügen nicht möglich");
        alert.showAndWait();
    }
}
