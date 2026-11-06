package de.richmarkdown.ui;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.settings.ApplicationSettings;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** The observable state shared by classic, Ribbon and favorite presentations. */
class EditorCommandStateTest {
    @BeforeAll static void toolkit() throws Exception {
        FxTestUtil.startToolkit();
        FxTestUtil.runAndWait(() -> {
            I18n.setSettingsStore(ApplicationSettings.inMemory()); I18n.setCurrentLanguage("de");
        });
    }

    @Test void stateChangesAreObservableAndRepeatingSameStateIsNotAnExtraChange() {
        FxTestUtil.runAndWait(() -> {
            EditorCommand command = new EditorCommand("format.bold", "Fett", "Text fett darstellen", true);
            AtomicInteger notifications = new AtomicInteger();
            command.enabledProperty().addListener((observable, before, after) -> notifications.incrementAndGet());
            command.update(true, true, true, null);
            assertTrue(command.isEnabled()); assertTrue(command.isVisible()); assertTrue(command.isSelected());
            assertTrue(command.isToggle()); assertEquals(1, notifications.get());
            command.update(true, true, true, null); assertEquals(1, notifications.get());
            command.update(false, false, false, null);
            assertFalse(command.isEnabled()); assertFalse(command.isVisible()); assertFalse(command.isSelected());
            assertEquals(2, notifications.get());
        });
    }

    @Test void explanationIncludesCurrentShortcutAndUnavailableContext() {
        FxTestUtil.runAndWait(() -> {
            EditorCommand command = new EditorCommand("edit.uppercase", "GROSSBUCHSTABEN", "Text umwandeln", false);
            var shortcut = new KeyCodeCombination(KeyCode.U, KeyCombination.CONTROL_DOWN);
            command.update(true, true, false, shortcut);
            assertEquals("Text umwandeln (" + shortcut.getDisplayText() + ")", command.explanation());
            assertSame(shortcut, command.shortcutProperty().get());
            command.update(false, true, false, null);
            assertTrue(command.explanation().startsWith("Text umwandeln\n"));
            assertTrue(command.explanation().contains("Kontext"));
        });
    }

    @Test void commandsFromDifferentEditorsNeverShareMutableState() {
        FxTestUtil.runAndWait(() -> {
            EditorCommand first = new EditorCommand("format.bold", "Fett", "Fett", true);
            EditorCommand second = new EditorCommand("format.bold", "Fett", "Fett", true);
            first.update(true, true, true, null); second.update(false, true, false, null);
            assertTrue(first.isEnabled()); assertTrue(first.isSelected());
            assertFalse(second.isEnabled()); assertFalse(second.isSelected());
            assertNotSame(first.enabledProperty(), second.enabledProperty());
            assertEquals(first.iconId(), second.iconId(), "The same action uses the same immutable icon identity");
        });
    }
}
