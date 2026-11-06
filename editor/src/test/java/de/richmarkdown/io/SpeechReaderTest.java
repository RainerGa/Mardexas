package de.richmarkdown.io;

import java.io.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpeechReaderTest {
    private static class FakeProcess extends Process {
        final ByteArrayOutputStream received = new ByteArrayOutputStream();
        final CountDownLatch written = new CountDownLatch(1); boolean destroyed;
        @Override public OutputStream getOutputStream() { return new FilterOutputStream(received) {
            @Override public void close() throws IOException { super.close(); written.countDown(); }
        }; }
        @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return 0; }
        @Override public int exitValue() { return 0; }
        @Override public void destroy() { destroyed = true; }
    }
    @Test void passesTextOnlyViaStdinAndStopsOnDispose() throws Exception {
        FakeProcess process = new FakeProcess();
        try (var reader = new SpeechReader("Linux", command -> { assertEquals(List.of("espeak", "--stdin"), command); return process; })) {
            reader.speak("Äpfel $(do-not-execute) **text**");
            assertTrue(process.written.await(3, TimeUnit.SECONDS));
            assertEquals("Äpfel $(do-not-execute) **text**", process.received.toString(java.nio.charset.StandardCharsets.UTF_8));
        }
        assertTrue(process.destroyed);
    }
    @Test void validatesLimitsBeforeStartingProcess() {
        try (var reader = new SpeechReader("Linux", command -> { fail("must not start"); return null; })) {
            assertThrows(IllegalArgumentException.class, () -> reader.speak(" "));
            assertThrows(IllegalArgumentException.class, () -> reader.speak("x".repeat(100_001)));
        }
    }
    @Test void exposesMissingSpeechEngineAndSelectsPlatforms() {
        assertEquals("say", SpeechReader.command("Mac OS X").get(0));
        assertEquals("powershell.exe", SpeechReader.command("Windows 11").get(0));
        try (var reader = new SpeechReader("Linux", command -> { throw new IOException("missing engine"); })) {
            assertThrows(IOException.class, () -> reader.speak("Hello"));
        }
    }
    @Test void reportsAsynchronousEngineFailure() throws Exception {
        var failure = new java.util.concurrent.LinkedBlockingQueue<IOException>();
        FakeProcess process = new FakeProcess() { @Override public int waitFor() { return 1; } };
        try (var reader = new SpeechReader("Linux", command -> process, failure::add)) {
            reader.speak("Hello");
            assertNotNull(failure.poll(3, TimeUnit.SECONDS)); assertTrue(process.destroyed);
        }
    }

}
