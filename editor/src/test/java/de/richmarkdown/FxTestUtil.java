package de.richmarkdown;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Helfer für headless UI-Tests (Monocle). Stellt sicher, dass das JavaFX-Toolkit
 * läuft, und führt Aktionen deterministisch auf dem FX-Thread aus.
 */
public final class FxTestUtil {

    private static volatile boolean toolkitStarted = false;

    private FxTestUtil() {
    }

    public static void startToolkit() throws InterruptedException {
        if (!toolkitStarted) {
            CountDownLatch latch = new CountDownLatch(1);
            try {
                Platform.startup(latch::countDown);
            } catch (IllegalStateException alreadyInitialized) {
                latch.countDown();
            }
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("JavaFX toolkit did not start headless");
            }
            toolkitStarted = true;
        }
    }

    /**
     * Führt die Aktion auf dem FX-Thread aus und wartet auf den Abschluss.
     * Ausnahmen der Aktion werden im aufrufenden Thread weitergeworfen.
     */
    public static void runAndWait(Runnable action) {
        callAndWait(() -> {
            action.run();
            return null;
        });
    }

    /**
     * Führt die Aktion auf dem FX-Thread aus, wartet auf den Abschluss und
     * gibt den Rückgabewert zurück.
     */
    public static <T> T callAndWait(Supplier<T> action) {
        if (Platform.isFxApplicationThread()) {
            return action.get();
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(action.get());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("FX action did not complete in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
        return result.get();
    }

    /**
     * Wartet, bis alle zuvor per {@code Platform.runLater} gequeueten Aktionen
     * abgearbeitet sind. Notwendig, weil {@code Action.execute()} der Bibliothek
     * asynchron auf dem FX-Thread läuft.
     */
    public static void flushFxEvents() {
        runAndWait(() -> {
        });
    }
}
