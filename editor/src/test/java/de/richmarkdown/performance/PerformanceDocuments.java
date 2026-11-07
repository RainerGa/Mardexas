package de.richmarkdown.performance;

/** Deterministic local fixtures: no network, image decoding or filesystem discovery. */
final class PerformanceDocuments {
    private PerformanceDocuments() { }

    static String markdown(int paragraphs) {
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < paragraphs; i++) {
            if (i % 20 == 0) source.append("## Kapitel ").append(i).append("\n\n");
            source.append("Absatz ").append(i)
                    .append(" mit **fettem Text**, *kursiv*, `code`, ~~Korrektur~~ und [Link](https://example.org).\n\n");
        }
        return source.toString();
    }
}
