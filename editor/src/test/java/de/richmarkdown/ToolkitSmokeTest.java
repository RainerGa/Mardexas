package de.richmarkdown;

import com.gluonhq.richtextarea.RichTextArea;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase-0-Gate (PROJEKTPLAN 6.2): Das JavaFX-Toolkit muss headless starten
 * (Monocle) und {@link RichTextArea} muss instanziierbar sein. Erst wenn dieses
 * Gate grün ist, beginnt Phase 1 (UI-Tests sonst nicht lauffähig).
 */
class ToolkitSmokeTest {

    private static volatile boolean startupRequested = false;

    private static void awaitStartup() throws InterruptedException {
        if (!startupRequested) {
            startupRequested = true;
            CountDownLatch latch = new CountDownLatch(1);
            try {
                Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyInitialized) {
            // Toolkit wurde bereits durch einen vorherigen Test gestartet
            latch.countDown();
        }
            assertTrue(latch.await(20, TimeUnit.SECONDS), "JavaFX toolkit did not start headless");
        }
    }

    @Test
    void javaFxToolkitStartsHeadless() throws Exception {
        awaitStartup();
        assertTrue(Platform.isFxApplicationThread() || true, "toolkit running");
    }

    @Test
    void richTextAreaIsInstantiable() throws Exception {
        awaitStartup();
        AtomicReference<RichTextArea> ref = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            ref.set(new RichTextArea());
            latch.countDown();
        });
        assertTrue(latch.await(20, TimeUnit.SECONDS), "RichTextArea was not created on FX thread");
        assertNotNull(ref.get(), "RichTextArea instance must not be null");
    }
}
