package de.richmarkdown.performance;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.Selection;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.markdown.MarkdownParser;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Measures FX dispatch through post-layout and rapid scrollbar traversal of a real shown scene. */
@Tag("performance")
class EditorRenderingPerformanceTest {
    private static final int SCROLL_WARMUP = 30;
    private static final int SCROLL_SAMPLES = 120;
    private MarkdownEditor editor;
    private Stage stage;
    private Scene scene;

    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }

    @ParameterizedTest
    @ValueSource(ints = {200, 400})
    void importFormatAndScroll(int paragraphs) throws Exception {
        runScenario(paragraphs);
    }

    @Test
    @EnabledIfSystemProperty(named = "perf.stress", matches = "true")
    void thousandParagraphStress() throws Exception { runScenario(1000); }

    @Test
    @EnabledIfSystemProperty(named = "perf.stress", matches = "true")
    void threeThousandParagraphStress() throws Exception { runScenario(3000); }

    @ParameterizedTest
    @ValueSource(strings = {"table", "long-inline"})
    @EnabledIfSystemProperty(named = "perf.shapes", matches = "true")
    void largeSingleParagraphShapes(String shape) throws Exception {
        String special = shape.equals("table")
                ? "| A | B | C |\n| --- | --- | --- |\n" + "| **eins** | zwei | `drei` |\n".repeat(500)
                : "Text **fett** und *kursiv* mit `code`. ".repeat(1000);
        runScenario("render-" + shape, PerformanceDocuments.markdown(200) + special, 200);
    }

    private void runScenario(int paragraphs) throws Exception {
        runScenario("render-" + paragraphs, PerformanceDocuments.markdown(paragraphs), paragraphs);
    }

    private void runScenario(String name, String source, int paragraphs) throws Exception {
        System.out.println("PERF " + name + " starting");
        var expected = new MarkdownParser().parseInto(source);
        var report = new PerformanceReport(name);
        try {
            onFx(() -> {
                editor = new MarkdownEditor();
                stage = new Stage();
                scene = new Scene(new StackPane(editor.getControl()), 900, 600);
                stage.setScene(scene); stage.show();
            });
            double[] imports = new double[3];
            for (int i = -1; i < imports.length; i++) {
                double elapsed = throughLayout(() -> editor.setMarkdown(source));
                onFx(() -> assertEquals(expected.getText(), editor.getDocumentSnapshot().getText()));
                if (i >= 0) imports[i] = elapsed;
            }
            report.metric("import_to_layout", "ms", imports);
            throughLayout(() -> ((RichTextAreaSkin) editor.getControl().getSkin()).getViewModel()
                    .setSelection(new Selection(0, 100)));
            double[] formats = new double[5];
            for (int i = -2; i < formats.length; i++) {
                double elapsed = throughLayout(editor::toggleBold);
                boolean bold = (i + 2) % 2 == 0;
                onFx(() -> assertEquals(bold, ((com.gluonhq.richtextarea.model.TextDecoration)
                        editor.getDocumentSnapshot().getDecorations().getFirst().getDecoration())
                        .getFontWeight() == javafx.scene.text.FontWeight.BOLD,
                        "The queued formatting command must have completed"));
                if (i >= 0) formats[i] = elapsed;
            }
            report.metric("bold_100_chars_to_layout", "ms", formats);
            onFx(() -> {
                assertEquals(expected.getText(), editor.getDocumentSnapshot().getText());
                assertEquals(100, editor.getControl().getSelection().getEnd());
                assertFalse(editor.getDocumentSnapshot().getDecorations().isEmpty());
            });
            scroll(report);
            report.write(source.length(), paragraphs, expected.getDecorations().size());
        } finally {
            onFx(() -> {
                if (stage != null) stage.close();
                if (editor != null && editor.getControl().getSkin() != null) editor.getControl().getSkin().dispose();
            });
        }
    }

    /** Includes queue latency, nested asynchronous open/decorate commands, CSS and next layout. */
    private double throughLayout(Runnable operation) throws Exception {
        var finished = new CompletableFuture<Double>();
        AtomicReference<Runnable> listener = new AtomicReference<>();
        long start = System.nanoTime();
        Platform.runLater(() -> {
            try {
                operation.run();
                // BasicAction and ViewModel both enqueue work; arm the observation after those queues.
                Platform.runLater(() -> Platform.runLater(() -> {
                    Runnable post = () -> {
                        scene.removePostLayoutPulseListener(listener.get());
                        finished.complete((System.nanoTime() - start) / 1e6);
                    };
                    listener.set(post);
                    scene.addPostLayoutPulseListener(post);
                    Platform.requestNextPulse();
                }));
            } catch (Throwable failure) { finished.completeExceptionally(failure); }
        });
        try { return finished.get(60, TimeUnit.SECONDS); }
        finally {
            onFx(() -> { if (listener.get() != null) scene.removePostLayoutPulseListener(listener.get()); });
        }
    }

    private void scroll(PerformanceReport report) throws Exception {
        var finished = new CompletableFuture<Void>();
        List<Double> intervals = new ArrayList<>(), layouts = new ArrayList<>(), handlers = new ArrayList<>();
        List<Double> cells = new ArrayList<>();
        AtomicReference<Runnable> preRef = new AtomicReference<>(), postRef = new AtomicReference<>();
        AtomicReference<AnimationTimer> timerRef = new AtomicReference<>();
        int[] itemCount = {0};
        onFx(() -> {
            var control = editor.getControl();
            var list = (ListView<?>) control.lookup(".paragraph-list-view");
            assertNotNull(list);
            itemCount[0] = list.getItems().size();
            var bar = (ScrollBar) control.lookup(".scroll-bar:vertical");
            assertNotNull(bar);
            assertTrue(bar.isVisible(), "Fixture must actually be scrollable");
            long[] preTime = {0}, lastPost = {0};
            double[] handlerMs = {0};
            int[] frames = {0};
            Runnable pre = () -> {
                try {
                    preTime[0] = System.nanoTime();
                    int step = frames[0] % 60;
                    double fraction = step <= 30 ? step / 30d : (60 - step) / 30d;
                    bar.setValue(bar.getMin() + fraction * (bar.getMax() - bar.getMin()));
                    handlerMs[0] = (System.nanoTime() - preTime[0]) / 1e6;
                } catch (Throwable failure) { finished.completeExceptionally(failure); }
            };
            Runnable post = () -> {
                try {
                    long now = System.nanoTime();
                    if (frames[0] >= SCROLL_WARMUP) {
                        intervals.add((now - lastPost[0]) / 1e6);
                        layouts.add((now - preTime[0]) / 1e6);
                        handlers.add(handlerMs[0]);
                        // Sample node count sparsely, after timing, to keep instrumentation small.
                        if (frames[0] % 30 == 0) cells.add((double) list.lookupAll(".list-cell").stream()
                                .filter(ListCell.class::isInstance).count());
                    }
                    lastPost[0] = now;
                    if (++frames[0] == SCROLL_WARMUP + SCROLL_SAMPLES) finished.complete(null);
                } catch (Throwable failure) { finished.completeExceptionally(failure); }
            };
            preRef.set(pre); postRef.set(post);
            scene.addPreLayoutPulseListener(pre); scene.addPostLayoutPulseListener(post);
            AnimationTimer timer = new AnimationTimer() { @Override public void handle(long now) { } };
            timerRef.set(timer); timer.start();
        });
        try { finished.get(60, TimeUnit.SECONDS); }
        finally {
            onFx(() -> {
                if (timerRef.get() != null) timerRef.get().stop();
                if (preRef.get() != null) scene.removePreLayoutPulseListener(preRef.get());
                if (postRef.get() != null) scene.removePostLayoutPulseListener(postRef.get());
            });
        }
        assertEquals(SCROLL_SAMPLES, intervals.size());
        double maxCells = cells.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        assertTrue(maxCells > 0 && maxCells < itemCount[0] / 2d,
                "Virtualization must keep materialized cells well below document paragraph count");
        report.metric("scroll_pulse_interval", "ms", array(intervals));
        report.metric("scroll_pre_to_post_layout", "ms", array(layouts));
        report.metric("scroll_handler", "ms", array(handlers));
        report.metric("materialized_cells", "count", array(cells));
        double elapsedMs = intervals.stream().mapToDouble(Double::doubleValue).sum();
        report.metric("scroll_pulses_per_second", "Hz", new double[]{1000 * intervals.size() / elapsedMs});
        report.metric("pulse_intervals_over_33ms", "percent", new double[]{100d * intervals.stream().filter(v -> v > 33.333).count() / intervals.size()});
    }

    private static double[] array(List<Double> values) { return values.stream().mapToDouble(Double::doubleValue).toArray(); }

    /** Dedicated timeout because large imports are the subject of this test. */
    private static void onFx(Runnable action) throws Exception {
        var finished = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            try { action.run(); finished.complete(null); }
            catch (Throwable failure) { finished.completeExceptionally(failure); }
        });
        finished.get(60, TimeUnit.SECONDS);
    }
}
