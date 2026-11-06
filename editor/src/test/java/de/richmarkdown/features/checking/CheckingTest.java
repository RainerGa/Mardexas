package de.richmarkdown.features.checking;

import de.richmarkdown.markdown.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CheckingTest {
    @TempDir Path directory;
    @Test void codeAndEscapedSyntaxNeverProduceLinkOrEmphasisWarnings() { String source = "```markdown\n[](bad)\n*literal\n### Title\n```\n\n~~~\n![](bad)\n~~~\n\n    [](bad)\n\n`[](bad)` \\[escaped](missing)\n\n* List item\n\n***"; assertTrue(MarkdownChecker.check(source).isEmpty(), MarkdownChecker.check(source).toString()); assertTrue(LinkManagement.links(source).isEmpty()); }
    @Test void fenceCharacterLengthAndTrailingLanguageAreHonoured() { assertTrue(MarkdownChecker.check("````java\n```\n[]()\n````\n").isEmpty()); assertEquals(1, MarkdownChecker.check("~~~text\n```\n").size()); assertEquals(1, MarkdownChecker.check("```\n```java\n").size()); }
    @Test void issueCoordinatesAreDocumentCoordinates() { MarkdownIssue issue = MarkdownChecker.check("Text\n\nVor [](url)").getFirst(); assertEquals(3, issue.line()); assertEquals(5, issue.column()); assertEquals(10, issue.position()); }
    @Test void inventoryBalancesParenthesesAndSkipsCode() { List<LinkManagement.Link> links = LinkManagement.links("[a](dir/file(1).md \"Titel\") ![Bild](<img file.png>) `[](skip)`"); assertEquals(2, links.size()); assertEquals("dir/file(1).md", links.getFirst().target()); assertEquals("img file.png", links.get(1).target()); assertTrue(links.get(1).image()); }
    @Test void localTargetsResolveRelativeEncodedNamesFragmentsAndPlus() throws Exception { Files.writeString(directory.resolve("a b+c.md"), "ok"); String source = "[ok](a%20b+c.md#titel) [intern](#titel) [web](https://example.org) [missing](fehlend.md)"; List<LinkManagement.Problem> problems = LinkManagement.localProblems(source, directory); assertEquals(1, problems.size()); assertTrue(problems.getFirst().message().contains("fehlend")); }
    @Test void webChecksAreExplicitDeduplicatedAndInjectable() { AtomicInteger calls = new AtomicInteger(); List<LinkManagement.Problem> problems = LinkManagement.webProblems("[a](https://example.org) [b](https://example.org) [c](other.md)", uri -> { calls.incrementAndGet(); return 404; }); assertEquals(1, calls.get()); assertEquals(2, problems.size()); assertThrows(IllegalArgumentException.class, () -> LinkManagement.probe(java.net.URI.create("file:///tmp/a"))); }
    @Test void replacementOnlyTouchesLinkDestinations() { assertEquals("old.md [x](new%20file.md) `[](old.md)`", LinkManagement.replaceTarget("old.md [x](old.md) `[](old.md)`", "old.md", "new file.md")); assertThrows(IllegalArgumentException.class, () -> LinkManagement.replaceTarget("", "x", "a\nb")); }
    @Test void advancedChecksReferencesLocalFilesAndQuickFix() { String source = "[Link][missing]\n\n[ok]: https://example.org\n[OK]: https://other.org\n\n[local](missing.md)"; List<MarkdownIssue> issues = AdvancedMarkdownCheck.check(source, directory); assertTrue(issues.stream().anyMatch(i -> i.message().contains("ohne Definition"))); assertTrue(issues.stream().anyMatch(i -> i.message().contains("Doppelte"))); assertTrue(issues.stream().anyMatch(i -> i.message().contains("Lokales"))); String broken = "~~~~\ncode\n"; MarkdownIssue issue = MarkdownChecker.check(broken).getFirst(); String fixed = AdvancedMarkdownCheck.quickFix(broken, issue).orElseThrow(); assertTrue(MarkdownChecker.check(fixed).isEmpty()); assertTrue(fixed.endsWith("~~~~\n")); }
    @Test void failedWebRequestsAndInvalidSchemesBecomeVisibleProblems() {
        List<LinkManagement.Problem> problems = LinkManagement.webProblems("[a](https://example.org)", uri -> { throw new java.io.IOException("Zeitüberschreitung"); });
        assertEquals(1, problems.size()); assertTrue(problems.getFirst().message().contains("Zeitüberschreitung"));
        assertThrows(IllegalArgumentException.class, () -> LinkManagement.probe(java.net.URI.create("relative")));
        assertThrows(IllegalArgumentException.class, () -> LinkManagement.probe(null));
    }
    @Test void staleQuickFixCoordinatesNeverChangeUnrelatedTextOrThrow() {
        MarkdownIssue fence = new MarkdownIssue(MarkdownIssue.Severity.ERROR, "Ungeschlossener Code-Fence", 0, 1, 1);
        for (String changed : List.of("", "   ", "Normaler Text", "``")) assertTrue(AdvancedMarkdownCheck.quickFix(changed, fence).isEmpty());
    }
    @Test void cancellationAvoidsFilesystemAndNetworkWork() {
        Thread.currentThread().interrupt();
        try { assertThrows(java.util.concurrent.CancellationException.class, () -> AdvancedMarkdownCheck.check("[x](missing.md)", directory)); }
        finally { Thread.interrupted(); }
    }
    @Test void inlineCodeMaskUsesMatchingRunLengthAndKeepsSourceOffsets() {
        String source = "Vor ``a ` [x](hidden)`` Nach [x](visible)";
        String masked = MarkdownSourceMask.prose(source);
        assertEquals(source.length(), masked.length()); assertEquals(1, LinkManagement.links(source).size()); assertEquals("visible", LinkManagement.links(source).getFirst().target());
    }
    @Test void manyUnmatchedBacktickLengthsHaveLinearScanningCost() {
        StringBuilder text = new StringBuilder(); for (int i = 1; i < 1500; i++) text.append("x ").append("`".repeat(i)).append(' ');
        assertTimeout(java.time.Duration.ofSeconds(3), () -> assertEquals(text.length(), MarkdownSourceMask.prose(text.toString()).length()));
    }
}
