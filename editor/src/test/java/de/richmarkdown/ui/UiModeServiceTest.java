package de.richmarkdown.ui;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.settings.ApplicationSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Application profile persistence, notification and error fallback without document writes. */
class UiModeServiceTest {
    @TempDir Path directory;
    private final List<Runnable> subscriptions = new ArrayList<>();

    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @BeforeEach void profile() {
        FxTestUtil.runAndWait(() -> {
            I18n.setSettingsStore(new ApplicationSettings(directory)); UiModeService.reset();
        });
    }
    @AfterEach void cleanup() {
        FxTestUtil.runAndWait(() -> {
            subscriptions.forEach(Runnable::run);
            I18n.setSettingsStore(ApplicationSettings.inMemory()); UiModeService.reset();
        });
    }

    private void subscribe(java.util.function.Consumer<UiMode> listener,
                           java.util.function.Consumer<RuntimeException> errors) {
        subscriptions.add(FxTestUtil.callAndWait(() -> UiModeService.subscribe(listener, errors)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown", "word", "ribbon2", "classic "})
    void invalidPreferenceDefaultsToClassic(String invalid) {
        assertEquals(UiMode.CLASSIC, UiMode.parse(invalid));
    }

    @Test void preferenceIsCaseIndependentAndDoesNotDependOnTurkishLocale() {
        var before = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertEquals(UiMode.RIBBON, UiMode.parse("ribbon"));
            assertEquals(UiMode.CLASSIC, UiMode.parse("CLASSIC"));
        } finally { java.util.Locale.setDefault(before); }
    }

    @Test void notifiesAllSubscribersAndPersistsOnlyApplicationPreference() throws Exception {
        List<UiMode> first = new ArrayList<>(); List<UiMode> second = new ArrayList<>();
        subscribe(first::add, error -> fail(error)); subscribe(second::add, error -> fail(error));
        FxTestUtil.runAndWait(() -> UiModeService.change(UiMode.RIBBON, error -> fail(error)));
        assertEquals(List.of(UiMode.CLASSIC, UiMode.RIBBON), first);
        assertEquals(first, second);
        ApplicationSettings reloaded = new ApplicationSettings(directory);
        assertEquals("ribbon", reloaded.read("features." + UiModeService.PREFERENCE));
        assertEquals(1, reloaded.snapshot().size(), "No Markdown or editor properties are persisted by switching mode");
        assertTrue(Files.isRegularFile(directory.resolve("RichMarkDownEditor/settings.properties")));
    }

    @Test void unsubscribeIsIdempotentAndStopsNotifications() {
        int baseline = FxTestUtil.callAndWait(UiModeService::subscriberCount);
        List<UiMode> observed = new ArrayList<>();
        subscribe(observed::add, error -> fail(error));
        assertEquals(baseline + 1, FxTestUtil.callAndWait(UiModeService::subscriberCount));
        FxTestUtil.runAndWait(() -> { subscriptions.getFirst().run(); subscriptions.getFirst().run();
            UiModeService.change(UiMode.RIBBON, error -> fail(error)); });
        assertEquals(List.of(UiMode.CLASSIC), observed);
        assertEquals(baseline, FxTestUtil.callAndWait(UiModeService::subscriberCount));
    }

    @Test void explicitProfileReloadReachesAlreadyOpenWindowsAndNewSubscribers() {
        List<UiMode> observed = new ArrayList<>();
        subscribe(observed::add, error -> fail(error));
        FxTestUtil.runAndWait(() -> {
            I18n.setPreference(UiModeService.PREFERENCE, "ribbon"); UiModeService.reset();
        });
        AtomicReference<UiMode> late = new AtomicReference<>();
        subscribe(late::set, error -> fail(error));
        assertEquals(UiMode.RIBBON, late.get());
        assertEquals(List.of(UiMode.CLASSIC, UiMode.RIBBON), observed);
    }

    @Test void writeFailureKeepsSessionModeAndReportsError() throws Exception {
        List<UiMode> observed = new ArrayList<>();
        subscribe(observed::add, error -> fail(error));
        Path blocker = directory.resolve("file-instead-of-profile-directory"); Files.writeString(blocker, "blocked");
        AtomicReference<RuntimeException> error = new AtomicReference<>();
        FxTestUtil.runAndWait(() -> {
            I18n.setSettingsStore(new ApplicationSettings(blocker));
            UiModeService.change(UiMode.RIBBON, error::set);
        });
        assertNotNull(error.get(), "The user must be told that the preference could not be stored");
        assertEquals(List.of(UiMode.CLASSIC, UiMode.RIBBON), observed,
                "The session must still be usable when profile persistence fails");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unreadableOrMalformedProfileReportsFailureAndRestoresUsableClassicMode(boolean malformed) throws Exception {
        List<UiMode> observed = new ArrayList<>(); subscribe(observed::add, error -> fail(error));
        FxTestUtil.runAndWait(() -> UiModeService.change(UiMode.RIBBON, error -> fail(error)));
        Path broken = directory.resolve("broken/RichMarkDownEditor/settings.properties");
        Files.createDirectories(broken.getParent());
        if (malformed) Files.writeString(broken, "invalid=" + (char) 92 + "uGGGG");
        else Files.createDirectory(broken);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        FxTestUtil.runAndWait(() -> {
            I18n.setSettingsStore(new ApplicationSettings(directory.resolve("broken")));
            UiModeService.reset(failure::set);
        });
        assertNotNull(failure.get());
        assertEquals(List.of(UiMode.CLASSIC, UiMode.RIBBON, UiMode.CLASSIC), observed);
    }

    @Test void subscriberMayUnsubscribeDuringNotification() {
        List<UiMode> observed = new ArrayList<>();
        AtomicReference<Runnable> stop = new AtomicReference<>(() -> { });
        subscribe(mode -> { observed.add(mode); if (mode == UiMode.RIBBON) stop.get().run(); }, error -> fail(error));
        stop.set(subscriptions.getFirst());
        FxTestUtil.runAndWait(() -> {
            UiModeService.change(UiMode.RIBBON, error -> fail(error));
            UiModeService.change(UiMode.CLASSIC, error -> fail(error));
        });
        assertEquals(List.of(UiMode.CLASSIC, UiMode.RIBBON), observed);
    }
}
