package de.richmarkdown.ui;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.Selection;
import com.gluonhq.richtextarea.model.Document;
import de.richmarkdown.editor.WritingMetrics;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.features.editing.DocumentEdits;
import java.time.Duration;
import java.util.Locale;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/** Selection/chapter statistics and session goals are computed only on demand. */
public final class WritingDashboard {
    private final EditorWindow window;
    private volatile long startWords;
    private volatile long started = System.nanoTime();
    private volatile int target;
    private Dialog<ButtonType> openDialog;
    public enum Scope { DOCUMENT, SELECTION, CHAPTER }
    public record Range(int start, int end) { }
    public static Range range(Document document, Scope scope, int selectionStart, int selectionEnd, int caret) {
        int length = document.getText().length();
        if (scope == Scope.SELECTION) {
            if (selectionStart < 0 || selectionEnd < selectionStart || selectionEnd > length) throw new IllegalArgumentException("Invalid selection");
            return new Range(selectionStart, selectionEnd);
        }
        if (scope == Scope.CHAPTER) {
            var chapters = de.richmarkdown.features.editing.ChapterModel.chapters(document);
            de.richmarkdown.features.editing.ChapterModel.Chapter current = null;
            for (var chapter : chapters) if (chapter.start() <= caret) current = chapter; else break;
            if (current != null) return new Range(current.start(), current.end());
            if (!chapters.isEmpty()) return new Range(0, chapters.get(0).start());
        }
        return new Range(0, length);
    }
    public WritingDashboard(EditorWindow window) {
        this.window = window;
        try { target = Integer.parseInt(I18n.preference("goal.target", "500")); }
        catch (NumberFormatException invalid) { target = 500; }
        if (target < 1 || target > 10_000_000) target = 500;
        startWords = window.getEditor().getStatistics().words();
    }
    public WritingMetrics.Goal goal() {
        return new WritingMetrics.Goal(target, startWords, window.getEditor().getStatistics().words(),
                Duration.ofNanos(Math.max(0, System.nanoTime() - started)));
    }
    public void reset(int words) {
        if (words < 1 || words > 10_000_000) throw new IllegalArgumentException("Target must be between 1 and 10000000");
        I18n.setPreference("goal.target", Integer.toString(words));
        target = words; startWords = window.getEditor().getStatistics().words(); started = System.nanoTime();
    }
    public static String scopedText(Document document, int start, int end) {
        if (start < 0 || end < start || end > document.getText().length()) throw new IllegalArgumentException("Invalid text range");
        return document.getText().substring(start, end).replace("\ufffc", "").replace("\u200b", "");
    }
    Range currentRange(Document snapshot, Scope scope) {
        Selection selection = window.getEditorControl().getSelection();
        int start = 0, end = 0;
        if (selection != null && selection.isDefined()) {
            var buffer = ((RichTextAreaSkin) window.getEditorControl().getSkin()).getViewModel().getTextBuffer();
            start = buffer.getText(0, selection.getStart()).length();
            end = buffer.getText(0, selection.getEnd()).length();
        }
        // The public caret is exported; only selections use internal offsets.
        return range(snapshot, scope, start, end, Math.max(0, window.getEditorControl().getCaretPosition()));
    }
    public void show() {
        if (openDialog != null) { openDialog.show(); openDialog.getDialogPane().requestFocus(); return; }
        ComboBox<String> scope = new ComboBox<>();
        scope.getItems().addAll(FeatureUi.text("Dokument", "Document"), FeatureUi.text("Auswahl", "Selection"), FeatureUi.text("Kapitel", "Chapter"));
        scope.getSelectionModel().select(0);
        scope.setId("writing-statistics-scope");
        Label statistics = new Label(); statistics.setId("writing-statistics-summary");
        ListView<String> frequency = new ListView<>();
        java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "mardexas-writing-analysis"); thread.setDaemon(true); return thread;
        });
        java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>> pending = new java.util.concurrent.atomic.AtomicReference<>();
        long[] revision = {0};
        Runnable refresh = () -> {
            var previous = pending.getAndSet(null);
            if (previous != null) previous.cancel(true);
            long request = ++revision[0];
            Document snapshot = window.getEditor().getDocumentSnapshot();
            Range range = currentRange(snapshot, Scope.values()[scope.getSelectionModel().getSelectedIndex()]);
            String text = scopedText(snapshot, range.start(), range.end());
            statistics.setText(FeatureUi.text("Berechnung …", "Analyzing …"));
            pending.set(worker.submit(() -> {
                try {
                    var analysis = WritingMetrics.analyze(text);
                    javafx.application.Platform.runLater(() -> {
                        if (request != revision[0] || worker.isShutdown()) return;
                        var stats = analysis.statistics();
                        statistics.setText(FeatureUi.text("Wörter: ", "Words: ") + stats.words()
                            + FeatureUi.text(" · Zeichen: ", " · Characters: ") + stats.charactersWithSpaces()
                            + FeatureUi.text(" · Wörter/Satz: ", " · Words/sentence: ") + String.format(Locale.ROOT, "%.1f", analysis.wordsPerSentence())
                            + FeatureUi.text("\nLängster Absatz: ", "\nLongest paragraph: ") + analysis.longestParagraph()
                            + (analysis.frequencyLimited() ? FeatureUi.text(" · Häufigkeitsliste begrenzt", " · Frequency list limited") : ""));
                        frequency.getItems().setAll(analysis.frequentWords().stream().map(term -> term.word() + " — " + term.count()).toList());
                    });
                } catch (java.util.concurrent.CancellationException cancelled) { /* superseded snapshot */ }
            }));
        };
        scope.valueProperty().addListener((observable, oldValue, value) -> refresh.run());
        Spinner<Integer> words = new Spinner<>(1, 10_000_000, target, 100); words.setEditable(true);
        words.setTooltip(new Tooltip(FeatureUi.text("Ziel für neu geschriebene Wörter dieser Sitzung", "Target for new words during this session")));
        ProgressBar progress = new ProgressBar(); progress.setMaxWidth(Double.MAX_VALUE);
        Label goal = new Label();
        Runnable refreshGoal = () -> {
            var state = goal(); progress.setProgress(state.progress());
            goal.setText(state.addedWords() + " / " + state.target() + FeatureUi.text(" neue Wörter · Minuten: ", " new words · minutes: ") + state.elapsed().toMinutes());
        };
        Button reset = FeatureUi.button(FeatureUi.text("Schreibziel starten", "Start writing goal"),
                FeatureUi.text("Ziel speichern und den Sitzungszähler zurücksetzen", "Save the target and reset the session counter"), () -> {
                    try { words.commitValue(); reset(words.getValue()); refreshGoal.run(); }
                    catch (RuntimeException failure) { FeatureUi.error(window.getDialogOwner(), failure); }
                });
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.initOwner(window.getDialogOwner());
        dialog.initModality(javafx.stage.Modality.NONE);
        openDialog = dialog;
        dialog.setTitle(FeatureUi.text("Schreibstatistik und Ziele", "Writing statistics and goals"));
        Button update = FeatureUi.button(FeatureUi.text("Aktualisieren", "Refresh"), FeatureUi.text("Statistik für den aktuellen Inhalt berechnen", "Analyze the current contents"), refresh);
        javafx.animation.PauseTransition debounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(800));
        debounce.setOnFinished(event -> { refreshGoal.run(); refresh.run(); });
        javafx.beans.value.ChangeListener<Number> listener = (observable, oldValue, value) -> debounce.playFromStart();
        window.getEditorControl().textLengthProperty().addListener(listener);
        window.getEditorControl().caretPositionProperty().addListener(listener);
        javafx.beans.value.ChangeListener<Selection> selectionChanged = (observable, oldValue, value) -> debounce.playFromStart();
        window.getEditorControl().selectionProperty().addListener(selectionChanged);
        Runnable documentChanged = debounce::playFromStart;
        window.getEditor().addChangeListener(documentChanged);
        Runnable disposer = dialog::close;
        dialog.setOnHidden(event -> {
            debounce.stop(); window.getEditorControl().textLengthProperty().removeListener(listener);
            window.getEditorControl().caretPositionProperty().removeListener(listener);
            window.getEditorControl().selectionProperty().removeListener(selectionChanged);
            window.getEditor().removeChangeListener(documentChanged);
            revision[0]++; worker.shutdownNow(); window.removeFeatureDisposer(disposer); openDialog = null;
        });
        window.addFeatureDisposer(disposer);
        dialog.getDialogPane().setContent(new VBox(10, scope, statistics, frequency, update, words, reset, progress, goal));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true); dialog.getDialogPane().setPrefSize(600, 600);
        refresh.run(); refreshGoal.run(); dialog.show();
    }
}
