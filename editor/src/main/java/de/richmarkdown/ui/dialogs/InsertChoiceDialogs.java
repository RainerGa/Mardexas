package de.richmarkdown.ui.dialogs;

import de.richmarkdown.i18n.I18n;
import de.richmarkdown.io.InsertionContent;
import de.richmarkdown.io.MarkdownLibraryStore;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.function.Consumer;

/** Visual pickers for special characters, snippet shortcuts and date/time formats. */
public final class InsertChoiceDialogs {
    private InsertChoiceDialogs() { }
    public static void characters(Window owner, Consumer<String> insert) {
        Dialog<String> dialog = new Dialog<>(); dialog.initOwner(owner); dialog.setTitle(I18n.text("dialog.specialCharacter.title")); dialog.setResizable(true);
        TextField query = new TextField(); query.setPromptText(WorkflowDialogs.text("search")); query.setId("character-search");
        TextField code = new TextField("U+00A9"); code.setId("character-code");
        ListView<Integer> list = new ListView<>(); Label glyph = new Label("©"); glyph.setStyle("-fx-font-size: 36px;");
        Label error = new Label(); error.getStyleClass().add("rme-dialog-error");
        list.setCellFactory(ignored -> new ListCell<>() { @Override protected void updateItem(Integer point, boolean empty) {
            super.updateItem(point, empty); setText(empty || point == null ? null : new String(Character.toChars(point)) + "   U+" + Integer.toHexString(point).toUpperCase(Locale.ROOT) + "   " + Character.getName(point));
        }});
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> { if (value != null) code.setText("U+" + Integer.toHexString(value)); });
        dialog.getDialogPane().setContent(new VBox(8, query, list, new HBox(10, glyph, code), error));
        dialog.getDialogPane().setPrefSize(700, 550); dialog.getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL);
        DialogButtons.applyDialogStyles(dialog.getDialogPane());
        Task<?>[] running = {null}; long[] revision = {0};
        Runnable search = () -> {
            if (running[0] != null) running[0].cancel(true);
            String term = query.getText().strip().toUpperCase(Locale.ROOT); long token = ++revision[0];
            Task<List<Integer>> task = new Task<>() { @Override protected List<Integer> call() {
                List<Integer> result = new ArrayList<>();
                for (int point = 32; point <= Character.MAX_CODE_POINT && result.size() < 500 && !isCancelled(); point++) {
                    if (!Character.isDefined(point) || Character.isISOControl(point) || point >= 0xd800 && point <= 0xdfff) continue;
                    String name = Character.getName(point);
                    if (term.isEmpty() ? point >= 0xa0 && point <= 0x2ff : name != null && (name.contains(term) || ("U+" + Integer.toHexString(point).toUpperCase(Locale.ROOT)).equals(term))) result.add(point);
                }
                return result;
            }};
            running[0] = task; task.setOnSucceeded(event -> { if (token == revision[0]) list.getItems().setAll(task.getValue()); });
            Thread.ofVirtual().name("character-search").start(task);
        };
        javafx.animation.PauseTransition debounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(200));
        debounce.setOnFinished(event -> search.run()); query.textProperty().addListener((obs, old, value) -> debounce.playFromStart());
        code.textProperty().addListener((obs, old, value) -> {
            try { glyph.setText(InsertionContent.codePoint(value)); error.setText(""); dialog.getDialogPane().lookupButton(DialogButtons.OK).setDisable(false); }
            catch (IllegalArgumentException failure) { error.setText(failure.getMessage()); dialog.getDialogPane().lookupButton(DialogButtons.OK).setDisable(true); }
        });
        dialog.setOnHidden(event -> { debounce.stop(); revision[0]++; if (running[0] != null) running[0].cancel(true); });
        dialog.setResultConverter(button -> button == DialogButtons.OK ? InsertionContent.codePoint(code.getText()) : null);
        search.run(); dialog.showAndWait().ifPresent(insert);
    }
    public static void snippets(Window owner, Consumer<String> insert) {
        Dialog<String> dialog = new Dialog<>(); dialog.initOwner(owner); dialog.setTitle(I18n.text("dialog.shortcut.title")); dialog.setResizable(true);
        TextField query = new TextField(); query.setPromptText(WorkflowDialogs.text("search"));
        ListView<MarkdownLibraryStore.Entry> list = new ListView<>(); TextArea preview = WorkflowDialogs.output(""); Label status = new Label();
        var all = javafx.collections.FXCollections.<MarkdownLibraryStore.Entry>observableArrayList();
        var filtered = new javafx.collections.transformation.FilteredList<>(all, entry -> true); list.setItems(filtered);
        list.setCellFactory(ignored -> new ListCell<>() { @Override protected void updateItem(MarkdownLibraryStore.Entry entry, boolean empty) {
            super.updateItem(entry, empty); setText(empty || entry == null ? null : entry.shortcut() + "  —  " + entry.name());
        }});
        query.textProperty().addListener((obs, old, value) -> { String term = value.toLowerCase(Locale.ROOT); filtered.setPredicate(entry -> (entry.name() + " " + entry.shortcut()).toLowerCase(Locale.ROOT).contains(term)); });
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> preview.setText(value == null ? "" : value.content()));
        VBox body = new VBox(8, query, list, preview, status); body.setPrefSize(650, 550); VBox.setVgrow(list, Priority.ALWAYS);
        dialog.getDialogPane().setContent(body); dialog.getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL); DialogButtons.applyDialogStyles(dialog.getDialogPane());
        dialog.getDialogPane().lookupButton(DialogButtons.OK).disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        Task<List<MarkdownLibraryStore.Entry>> task = new Task<>() { @Override protected List<MarkdownLibraryStore.Entry> call() throws Exception { return MarkdownLibraryStore.userLibrary(MarkdownLibraryStore.Kind.SNIPPET).list(); }};
        task.setOnSucceeded(event -> { all.setAll(task.getValue()); if (!all.isEmpty()) list.getSelectionModel().selectFirst(); else status.setText(I18n.text("dialog.shortcut.noSnippet")); });
        task.setOnFailed(event -> status.setText(task.getException().getMessage()));
        dialog.setOnHidden(event -> task.cancel(true));
        dialog.setResultConverter(button -> button == DialogButtons.OK ? list.getSelectionModel().getSelectedItem().content() : null);
        Thread.ofVirtual().name("snippet-picker").start(task);
        dialog.showAndWait().flatMap(value -> TemplateFieldsDialog.resolve(owner, value)).ifPresent(insert);
    }
    public static void dateTime(Window owner, Consumer<String> insert) {
        Dialog<String> dialog = new Dialog<>(); dialog.initOwner(owner); dialog.setTitle(I18n.text("dialog.dateTime.title"));
        Locale locale = Locale.forLanguageTag(I18n.currentLanguageCode().replace('_', '-')); ZonedDateTime now = ZonedDateTime.now();
        List<String> formats = List.of("yyyy-MM-dd", "dd.MM.yyyy", "yyyy-MM-dd HH:mm", "HH:mm", "HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ssXXX");
        ComboBox<String> format = new ComboBox<>(); format.getItems().setAll(formats); format.setEditable(true); format.setValue(formats.getFirst()); format.getEditor().setText(formats.getFirst());
        Label preview = new Label(); Label error = new Label();
        dialog.getDialogPane().setContent(new VBox(8, new Label(locale.getDisplayName(locale) + " · " + now.getZone()), format, preview, error));
        dialog.getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL); DialogButtons.applyDialogStyles(dialog.getDialogPane());
        Runnable validate = () -> { try { preview.setText(DateTimeFormatter.ofPattern(format.getEditor().getText(), locale).format(now)); error.setText(""); dialog.getDialogPane().lookupButton(DialogButtons.OK).setDisable(preview.getText().isBlank()); }
            catch (IllegalArgumentException | DateTimeException failure) { error.setText(failure.getMessage()); dialog.getDialogPane().lookupButton(DialogButtons.OK).setDisable(true); } };
        format.getEditor().textProperty().addListener((obs, old, value) -> validate.run()); validate.run();
        dialog.setResultConverter(button -> button == DialogButtons.OK ? preview.getText() : null); dialog.showAndWait().ifPresent(insert);
    }
}
