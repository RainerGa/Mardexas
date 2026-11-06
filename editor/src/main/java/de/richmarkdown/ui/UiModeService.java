package de.richmarkdown.ui;

import de.richmarkdown.i18n.I18n;
import java.util.WeakHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** FX-thread application preference, with explicit subscriptions and session fallback on IO failure. */
public final class UiModeService {
    public static final String PREFERENCE = "ui.commandSurface";
    private record Subscriber(Consumer<UiMode> listener, Consumer<RuntimeException> failure) { }
    // The unsubscribe handle owns its token. Abandoned, never-shown editor windows
    // therefore do not leave permanent application-level subscriptions behind.
    private static final Map<Object, Subscriber> listeners = new WeakHashMap<>();
    private static UiMode mode = UiMode.CLASSIC;
    private UiModeService() { }

    static Runnable subscribe(Consumer<UiMode> listener, Consumer<RuntimeException> failure) {
        if (listeners.isEmpty()) {
            try { mode = UiMode.parse(I18n.preference(PREFERENCE, "classic")); }
            catch (RuntimeException error) { mode = UiMode.CLASSIC; failure.accept(error); }
        }
        Object token = new Object();
        listeners.put(token, new Subscriber(listener, failure));
        listener.accept(mode);
        return () -> listeners.remove(token);
    }

    static void change(UiMode value, Consumer<RuntimeException> failure) {
        mode = Objects.requireNonNull(value);
        for (var listener : java.util.List.copyOf(listeners.values())) listener.listener().accept(mode);
        try { I18n.setPreference(PREFERENCE, mode.name().toLowerCase(java.util.Locale.ROOT)); }
        catch (RuntimeException error) { failure.accept(error); }
    }

    /** Reloading a profile is explicit; useful for embedders and isolated GUI tests. */
    public static void reset() {
        reset(error -> {
            if (!listeners.isEmpty()) listeners.values().iterator().next().failure().accept(error);
        });
    }

    /** Reloads a profile and reports failure while retaining a usable classic session. */
    public static void reset(Consumer<RuntimeException> failure) {
        RuntimeException problem = null;
        try { mode = UiMode.parse(I18n.preference(PREFERENCE, "classic")); }
        catch (RuntimeException error) { mode = UiMode.CLASSIC; problem = error; }
        for (var listener : java.util.List.copyOf(listeners.values())) listener.listener().accept(mode);
        if (problem != null) failure.accept(problem);
    }

    static int subscriberCount() { return listeners.size(); }
}
