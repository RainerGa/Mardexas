package de.richmarkdown.performance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceReportTest {
    @Test void percentilesUseNearestRankWithoutMutatingSamples() {
        double[] samples = {20, 1, 5, 2};
        var stats = PerformanceReport.summarize(samples);
        assertEquals(7, stats.mean());
        assertEquals(2, stats.p50());
        assertEquals(20, stats.p95());
        assertEquals(20, samples[0]);
        assertThrows(IllegalArgumentException.class, () -> PerformanceReport.summarize(new double[0]));
        assertThrows(IllegalArgumentException.class, () -> PerformanceReport.summarize(new double[]{Double.NaN}));
    }

    @Test void reportPreservesRawSamplesAndUnits(@TempDir Path directory) throws Exception {
        String old = System.getProperty("perf.outputDirectory");
        System.setProperty("perf.outputDirectory", directory.toString());
        try {
            var report = new PerformanceReport("example");
            report.metric("parse", "ms", new double[]{1.5, 2.5});
            report.write(100, 2, 4);
            assertEquals("parse,ms,0,1.500000", Files.readAllLines(directory.resolve("example-samples.csv")).get(1));
            assertTrue(Files.readString(directory.resolve("example-summary.csv")).contains("parse,ms,2,2.000000"));
            assertTrue(Files.readString(directory.resolve("example.properties")).contains("sourceChars=100"));
        } finally {
            if (old == null) System.clearProperty("perf.outputDirectory");
            else System.setProperty("perf.outputDirectory", old);
        }
    }
}
