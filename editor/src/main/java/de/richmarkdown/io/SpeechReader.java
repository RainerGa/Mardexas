package de.richmarkdown.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/** Uses the platform speech engine without a shell, passing document text solely on stdin. */
public final class SpeechReader implements AutoCloseable {
    @FunctionalInterface public interface Launcher { Process start(List<String> command) throws IOException; }
    private final Launcher launcher;
    private final String operatingSystem;
    private Process running;
    private final java.util.function.Consumer<IOException> onFailure;
    public SpeechReader() { this(error -> java.util.logging.Logger.getLogger(SpeechReader.class.getName()).log(java.util.logging.Level.WARNING, "Speech output failed", error)); }
    public SpeechReader(java.util.function.Consumer<IOException> onFailure) { this(System.getProperty("os.name", ""), command -> new ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start(), onFailure); }
    public SpeechReader(String operatingSystem, Launcher launcher) { this(operatingSystem, launcher, error -> { }); }
    public SpeechReader(String operatingSystem, Launcher launcher, java.util.function.Consumer<IOException> onFailure) {
        this.onFailure = Objects.requireNonNull(onFailure);
        this.operatingSystem = Objects.requireNonNull(operatingSystem); this.launcher = Objects.requireNonNull(launcher);
    }
    public static List<String> command(String os) {
        if (os.startsWith("Mac")) return List.of("say");
        if (os.startsWith("Windows")) return List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                "Add-Type -AssemblyName System.Speech; $s=New-Object System.Speech.Synthesis.SpeechSynthesizer; $s.Speak([Console]::In.ReadToEnd()); $s.Dispose()");
        return List.of("espeak", "--stdin");
    }
    public synchronized void speak(String text) throws IOException {
        Objects.requireNonNull(text);
        if (text.isBlank()) throw new IllegalArgumentException("Please select text to read");
        if (text.length() > 100_000) throw new IllegalArgumentException("Please select at most 100000 characters");
        stop();
        Process process = launcher.start(command(operatingSystem)); running = process;
        // Keep JavaFX responsive while writing a large selection to a slow speech process.
        Thread writer = new Thread(() -> {
            try {
                try (var stream = process.getOutputStream()) { stream.write(text.getBytes(StandardCharsets.UTF_8)); }
                if (process.waitFor() != 0) reportFailure(process, new IOException("Speech engine exited with an error"));
            } catch (IOException failure) { reportFailure(process, failure); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }, "mardexas-speech-input");
        writer.setDaemon(true); writer.start();
    }
    private void reportFailure(Process process, IOException failure) {
        synchronized (this) {
            if (running != process) return;
            running = null; process.destroy();
        }
        onFailure.accept(failure);
    }
    public synchronized void stop() { if (running != null) { running.destroy(); running = null; } }
    @Override public void close() { stop(); }
}
