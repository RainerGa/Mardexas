package de.richmarkdown.features.checking;

import de.richmarkdown.markdown.MarkdownIssue;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Debounced single-worker checking. Source capture and results stay on the FX thread. */
public final class LiveMarkdownCheck implements AutoCloseable {
    private final Supplier<String> source;
    private final Supplier<Path> directory;
    private final Consumer<List<MarkdownIssue>> consumer;
    private final PauseTransition debounce = new PauseTransition(Duration.millis(600));
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), r -> { Thread t = new Thread(r, "mardexas-markdown-check"); t.setDaemon(true); return t; });
    private final Set<String> ignored = new HashSet<>();
    private Future<?> pending;
    private long revision;
    private boolean closed;
    public LiveMarkdownCheck(Supplier<String> source, Supplier<Path> directory, Consumer<List<MarkdownIssue>> consumer) {
        this.source = Objects.requireNonNull(source); this.directory = Objects.requireNonNull(directory); this.consumer = Objects.requireNonNull(consumer);
        debounce.setOnFinished(event -> checkNow());
    }
    public void request() { if (!closed) { revision++; debounce.playFromStart(); if (pending != null) pending.cancel(true); } }
    public void ignore(MarkdownIssue issue) { ignored.add(issue.message()); request(); }
    public void clearIgnored() { ignored.clear(); request(); }
    public void checkNow() {
        if (closed) return; debounce.stop(); long requested = ++revision; if (pending != null) pending.cancel(true);
        String text; Path path;
        try { text = source.get(); path = directory.get(); }
        catch (RuntimeException failure) { consumer.accept(List.of(failed(failure))); return; }
        Set<String> exclusions = Set.copyOf(ignored);
        try {
            WORKERS.purge(); pending = WORKERS.submit(() -> {
                List<MarkdownIssue> issues;
                try { issues = AdvancedMarkdownCheck.check(text, path).stream().filter(issue -> !exclusions.contains(issue.message())).toList(); }
                catch (CancellationException interrupted) { return; }
                catch (RuntimeException failure) { issues = List.of(failed(failure)); }
                if (Thread.currentThread().isInterrupted()) return;
                List<MarkdownIssue> complete = issues; Platform.runLater(() -> { if (!closed && requested == revision) consumer.accept(complete); });
            });
        } catch (RejectedExecutionException busy) { consumer.accept(List.of(new MarkdownIssue(MarkdownIssue.Severity.HINT, "Markdown-Prüfung ausgelastet. Bitte erneut prüfen.", 0, 1, 1))); }
    }
    private static MarkdownIssue failed(RuntimeException failure) { return new MarkdownIssue(MarkdownIssue.Severity.ERROR, "Markdown-Prüfung fehlgeschlagen: " + failure.getMessage(), 0, 1, 1); }
    @Override public void close() { closed = true; revision++; debounce.stop(); if (pending != null) pending.cancel(true); WORKERS.purge(); }
}
