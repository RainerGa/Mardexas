package de.richmarkdown.editor;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Linear text analysis. Frequency results are bounded and never modify the document. */
public final class WritingMetrics {
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:['’\\-][\\p{L}\\p{N}]+)*");
    private static final int MAX_TERMS = 50_000;
    public record Term(String word, int count) { }
    public record Analysis(DocumentStatistics statistics, List<Term> frequentWords,
                           double wordsPerSentence, int longestParagraph, boolean frequencyLimited) {
        public Analysis { frequentWords = List.copyOf(frequentWords); }
    }
    public record Goal(int target, long startWords, long currentWords, Duration elapsed) {
        public Goal {
            if (target < 1 || target > 10_000_000 || startWords < 0 || currentWords < 0 || elapsed.isNegative()) {
                throw new IllegalArgumentException("Invalid writing goal");
            }
        }
        public long addedWords() { return Math.max(0, currentWords - startWords); }
        public double progress() { return Math.min(1.0, (double) addedWords() / target); }
    }
    private WritingMetrics() { }

    public static Analysis analyze(String text) {
        if (text == null) throw new NullPointerException("text");
        DocumentStatistics statistics = DocumentStatistics.of(text, 0, 0);
        Map<String, Integer> counts = new HashMap<>();
        boolean limited = false;
        var matcher = WORD.matcher(text);
        while (matcher.find()) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            if (matcher.end() - matcher.start() > 256) { limited = true; continue; }
            String word = matcher.group().toLowerCase(Locale.ROOT);
            if (counts.containsKey(word) || counts.size() < MAX_TERMS) counts.merge(word, 1, Integer::sum);
            else limited = true;
        }
        int longest = 0, start = 0;
        for (int index = 0; index <= text.length(); index++) {
            if ((index & 4095) == 0 && Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            if (index == text.length() || text.charAt(index) == '\n') {
                longest = Math.max(longest, index - start); start = index + 1;
            }
        }
        List<Term> frequent = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(50).map(entry -> new Term(entry.getKey(), entry.getValue())).toList();
        return new Analysis(statistics, frequent,
                statistics.sentences() == 0 ? 0 : (double) statistics.words() / statistics.sentences(), longest, limited);
    }
}
