package de.richmarkdown;

import java.util.Locale;
import java.util.Properties;

/** Graphics defaults that must be installed before the JavaFX toolkit starts. */
final class FxRuntimeOptions {
    private FxRuntimeOptions() { }

    static void configure() {
        configure(System.getProperties());
    }

    static void configure(Properties properties) {
        // OpenJFX explicitly provides this painter to avoid Linux live-resize
        // jitter. Render to an offscreen buffer and upload completed frames,
        // rather than drawing directly into a window while GTK resizes it.
        // Keep the selected GPU pipeline and honour explicit user overrides.
        if (properties.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("linux")) {
            properties.putIfAbsent("prism.forceUploadingPainter", "true");
        }
    }
}
