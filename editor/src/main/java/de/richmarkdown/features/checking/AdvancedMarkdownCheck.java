package de.richmarkdown.features.checking;

import de.richmarkdown.markdown.*;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;

/** Additional checks never add markers or hidden data to a document. */
public final class AdvancedMarkdownCheck {
    private static final Pattern DEFINITION = Pattern.compile("(?m)^ {0,3}\\[([^]\\n]+)]:\\s*\\S+.*$");
    private static final Pattern REFERENCE = Pattern.compile("(?<!!)\\[([^]\\n]+)]\\[([^]\\n]*)]");
    private AdvancedMarkdownCheck() {}
    public static List<MarkdownIssue> check(String source, Path directory) {
        if (source == null || source.isBlank()) return List.of();
        if (source.length() > 8_000_000) return List.of(new MarkdownIssue(MarkdownIssue.Severity.HINT, "Automatische Prüfung für sehr große Dokumente ausgesetzt.", 0,1,1));
        List<MarkdownIssue> result = new ArrayList<>(MarkdownChecker.check(source));
        int[] lineStarts = java.util.stream.IntStream.concat(java.util.stream.IntStream.of(0), java.util.stream.IntStream.range(0, source.length()).filter(i -> source.charAt(i) == '\n').map(i -> i + 1)).toArray();
        for (LinkManagement.Problem problem : LinkManagement.localProblems(source, directory)) {
            interrupted();
            // Unsaved relative links are checked on demand after a location is known.
            if (directory != null || problem.link().target().startsWith("file:")) result.add(issue(lineStarts, problem.link().start(), problem.message(), MarkdownIssue.Severity.WARNING));
        }
        String masked = MarkdownSourceMask.prose(source); Set<String> definitions = new HashSet<>(); Matcher definition = DEFINITION.matcher(masked);
        while (definition.find()) { interrupted(); String label = label(definition.group(1)); if (!definitions.add(label)) result.add(issue(lineStarts, definition.start(), "Doppelte Referenzlink-Definition: " + label, MarkdownIssue.Severity.WARNING)); }
        Matcher reference = REFERENCE.matcher(masked);
        while (reference.find()) { interrupted(); String target = reference.group(2).isBlank() ? reference.group(1) : reference.group(2); if (!definitions.contains(label(target))) result.add(issue(lineStarts, reference.start(), "Referenzlink ohne Definition: " + target, MarkdownIssue.Severity.WARNING)); }
        return List.copyOf(result);
    }
    private static void interrupted() { if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException(); }
    private static String label(String label) { return label.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
    private static MarkdownIssue issue(int[] starts, int position, String message, MarkdownIssue.Severity severity) {
        int found = Arrays.binarySearch(starts, position); int line = found >= 0 ? found : -found - 2;
        return new MarkdownIssue(severity, message, position, line + 1, position - starts[line] + 1);
    }

    public static Optional<String> quickFix(String source, MarkdownIssue issue) {
        if (!issue.message().equals("Ungeschlossener Code-Fence") || issue.position() >= source.length()) return Optional.empty();
        int start = issue.position(); while (start < source.length() && source.charAt(start) == ' ') start++;
        if (start >= source.length()) return Optional.empty();
        char marker = source.charAt(start); int end = start; while (end < source.length() && source.charAt(end) == marker) end++;
        if ((marker != '`' && marker != '~') || end - start < 3) return Optional.empty();
        return Optional.of(source + (source.endsWith("\n") ? "" : "\n") + String.valueOf(marker).repeat(end - start) + "\n");
    }
}
