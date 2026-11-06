package de.richmarkdown.editor;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WritingMetricsTest {
    @Test void analyzesVisibleTextAndStableFrequencyOrder() {
        var metrics = WritingMetrics.analyze("Äpfel Birnen ÄPFEL.\nBirnen gut!");
        assertEquals(5, metrics.statistics().words());
        assertEquals(new WritingMetrics.Term("birnen", 2), metrics.frequentWords().get(0));
        assertEquals(new WritingMetrics.Term("äpfel", 2), metrics.frequentWords().get(1));
        assertEquals(2.5, metrics.wordsPerSentence()); assertEquals(19, metrics.longestParagraph());
        assertThrows(UnsupportedOperationException.class, () -> metrics.frequentWords().clear());
    }
    @Test void emptyInputIsSafeAndLimitsResults() {
        assertEquals(0, WritingMetrics.analyze("").wordsPerSentence());
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 60_000; i++) text.append("word").append(i).append(' ');
        var analysis = WritingMetrics.analyze(text.toString());
        assertEquals(50, analysis.frequentWords().size()); assertTrue(analysis.frequencyLimited());
    }
    @Test void avoidsRetainingEnormousSingleWords() {
        var metrics = WritingMetrics.analyze("a".repeat(300));
        assertTrue(metrics.frequentWords().isEmpty()); assertTrue(metrics.frequencyLimited());
    }
    @Test void goalsCountNetNewWordsAndClampProgress() {
        var goal = new WritingMetrics.Goal(100, 200, 250, Duration.ofMinutes(2));
        assertEquals(50, goal.addedWords()); assertEquals(0.5, goal.progress());
        assertEquals(0, new WritingMetrics.Goal(10, 20, 2, Duration.ZERO).progress());
        assertEquals(1, new WritingMetrics.Goal(10, 0, 50, Duration.ZERO).progress());
        assertThrows(IllegalArgumentException.class, () -> new WritingMetrics.Goal(0, 0, 0, Duration.ZERO));
    }
}
