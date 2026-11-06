package de.richmarkdown.features.checking;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.markdown.MarkdownIssue;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class LiveMarkdownCheckTest {
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @Test void deliversOnFxThreadAndSupportsIgnoredRulesAndClose() throws Exception {
        BlockingQueue<List<MarkdownIssue>> results = new LinkedBlockingQueue<>(); AtomicReference<String> source = new AtomicReference<>("[](url)");
        LiveMarkdownCheck controller = FxTestUtil.callAndWait(() -> new LiveMarkdownCheck(source::get, () -> null, issues -> { assertTrue(javafx.application.Platform.isFxApplicationThread()); results.add(issues); }));
        try { FxTestUtil.runAndWait(controller::checkNow); List<MarkdownIssue> first = results.poll(5, TimeUnit.SECONDS); assertNotNull(first); assertEquals(1, first.size());
            FxTestUtil.runAndWait(() -> { controller.ignore(first.getFirst()); controller.checkNow(); }); assertEquals(List.of(), results.poll(5, TimeUnit.SECONDS));
            FxTestUtil.runAndWait(() -> { controller.clearIgnored(); source.set("plain"); controller.checkNow(); }); assertEquals(List.of(), results.poll(5, TimeUnit.SECONDS));
        } finally { FxTestUtil.runAndWait(controller::close); }
        FxTestUtil.runAndWait(() -> { controller.request(); controller.checkNow(); }); FxTestUtil.flushFxEvents(); assertTrue(results.isEmpty());
    }
    @Test void sourceCaptureFailuresAreReportedAndClosingSuppressesPendingResults() throws Exception {
        BlockingQueue<List<MarkdownIssue>> results = new LinkedBlockingQueue<>();
        LiveMarkdownCheck broken = FxTestUtil.callAndWait(() -> new LiveMarkdownCheck(() -> { throw new IllegalStateException("Nicht bereit"); }, () -> null, results::add));
        try { FxTestUtil.runAndWait(broken::checkNow); assertTrue(results.remove().getFirst().message().contains("Nicht bereit")); }
        finally { FxTestUtil.runAndWait(broken::close); }
        LiveMarkdownCheck pending = FxTestUtil.callAndWait(() -> new LiveMarkdownCheck(() -> "[](url)", () -> null, results::add));
        FxTestUtil.runAndWait(() -> { pending.checkNow(); pending.close(); });
        FxTestUtil.flushFxEvents(); assertNull(results.poll(200, TimeUnit.MILLISECONDS));
    }
}
