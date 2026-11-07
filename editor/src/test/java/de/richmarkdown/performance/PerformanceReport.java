package de.richmarkdown.performance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** Raw samples plus nearest-rank percentiles; deliberately no machine-dependent default limits. */
final class PerformanceReport {
    private final String scenario;
    private final List<String> rows = new ArrayList<>(List.of("metric,unit,sample,value"));
    private final List<String> summaries = new ArrayList<>(List.of("metric,unit,count,mean,p50,p95,max"));

    PerformanceReport(String scenario) { this.scenario = scenario; }

    record Summary(int count, double mean, double p50, double p95, double max) { }

    static Summary summarize(double[] values) {
        if (values.length == 0 || Arrays.stream(values).anyMatch(v -> !Double.isFinite(v) || v < 0)) {
            throw new IllegalArgumentException("Expected finite nonnegative samples");
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return new Summary(sorted.length, Arrays.stream(sorted).average().orElseThrow(),
                sorted[(int) Math.ceil(sorted.length * .5) - 1],
                sorted[(int) Math.ceil(sorted.length * .95) - 1], sorted[sorted.length - 1]);
    }

    void metric(String name, String unit, double[] values) {
        Summary s = summarize(values);
        for (int i = 0; i < values.length; i++) {
            rows.add(String.format(Locale.ROOT, "%s,%s,%d,%.6f", name, unit, i, values[i]));
        }
        summaries.add(String.format(Locale.ROOT, "%s,%s,%d,%.6f,%.6f,%.6f,%.6f",
                name, unit, s.count(), s.mean(), s.p50(), s.p95(), s.max()));
        System.out.printf(Locale.ROOT, "PERF %s %s: mean=%.3f p95=%.3f max=%.3f %s%n",
                scenario, name, s.mean(), s.p95(), s.max(), unit);
    }

    void write(int sourceChars, int paragraphs, int decorations) throws IOException {
        Path directory = Path.of(System.getProperty("perf.outputDirectory", "target/performance"));
        Files.createDirectories(directory);
        Files.write(directory.resolve(scenario + "-samples.csv"), rows);
        Files.write(directory.resolve(scenario + "-summary.csv"), summaries);
        Properties metadata = new Properties();
        for (String key : List.of("java.version", "java.vm.name", "javafx.runtime.version", "os.name", "os.arch",
                "os.version", "prism.order", "glass.platform", "monocle.platform")) {
            metadata.setProperty(key, System.getProperty(key, "unknown"));
        }
        metadata.setProperty("timestamp.utc", Instant.now().toString());
        metadata.setProperty("processors", Integer.toString(Runtime.getRuntime().availableProcessors()));
        metadata.setProperty("maxHeapBytes", Long.toString(Runtime.getRuntime().maxMemory()));
        metadata.setProperty("sourceChars", Integer.toString(sourceChars));
        metadata.setProperty("paragraphs", Integer.toString(paragraphs));
        metadata.setProperty("decorations", Integer.toString(decorations));
        metadata.setProperty("viewport", scenario.startsWith("render-") ? "900x600" : "not-applicable");
        try (var output = Files.newOutputStream(directory.resolve(scenario + ".properties"))) {
            metadata.store(output, "Mardexas performance run; preserve with matching git revisions");
        }
    }
}
