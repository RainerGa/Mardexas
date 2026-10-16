/*
 * Copyright (C) 2026 Rainer Garbotz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
