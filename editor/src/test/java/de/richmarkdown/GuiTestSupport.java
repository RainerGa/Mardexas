package de.richmarkdown;

import javafx.animation.AnimationTimer;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.robot.Robot;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real Glass input and rendered pulses, shared by the headless Stage regression tests. */
public final class GuiTestSupport {
    private GuiTestSupport() { }

    /** JavaFX menu accelerators use Command on macOS and Control elsewhere. */
    public static KeyCode shortcutModifier() {
        return System.getProperty("os.name", "").startsWith("Mac") ? KeyCode.META : KeyCode.CONTROL;
    }

    public static void shortcut(KeyCode key, boolean shift) throws InterruptedException {
        FxTestUtil.runAndWait(() -> {
            Robot robot = new Robot();
            KeyCode modifier = shortcutModifier();
            robot.keyPress(modifier);
            try {
                if (shift) robot.keyPress(KeyCode.SHIFT);
                try {
                    robot.keyPress(key);
                    robot.keyRelease(key);
                } finally {
                    if (shift) robot.keyRelease(KeyCode.SHIFT);
                }
            } finally {
                robot.keyRelease(modifier);
            }
        });
        pulses();
    }

    public static void pulses() throws InterruptedException {
        CountDownLatch rendered = new CountDownLatch(1);
        FxTestUtil.runAndWait(() -> new AnimationTimer() {
            private int frames;
            @Override public void handle(long now) {
                if (++frames == 4) { stop(); rendered.countDown(); }
            }
        }.start());
        assertTrue(rendered.await(FxTestUtil.TIMEOUT_SECONDS, TimeUnit.SECONDS), "The shown scene must render");
        FxTestUtil.flushFxEvents();
    }

    public static void click(Node node, double localX, double localY) throws InterruptedException {
        Point2D point = FxTestUtil.callAndWait(() -> node.localToScreen(localX, localY));
        clickScreen(point.getX(), point.getY());
    }

    public static void clickScreen(double x, double y) throws InterruptedException {
        FxTestUtil.runAndWait(() -> new Robot().mouseMove(x, y));
        FxTestUtil.flushFxEvents();
        FxTestUtil.runAndWait(() -> new Robot().mousePress(MouseButton.PRIMARY));
        FxTestUtil.flushFxEvents();
        FxTestUtil.runAndWait(() -> new Robot().mouseRelease(MouseButton.PRIMARY));
        pulses();
    }

    public static void key(KeyCode key) throws InterruptedException {
        FxTestUtil.runAndWait(() -> new Robot().keyPress(key));
        FxTestUtil.flushFxEvents();
        FxTestUtil.runAndWait(() -> new Robot().keyRelease(key));
        pulses();
    }
}
