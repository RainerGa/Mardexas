package de.richmarkdown.features.checking;

import java.net.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Link inventory and explicit checks. Local checks never open a network connection. */
public final class LinkManagement {
    private LinkManagement() {}
    public record Link(int start, int end, int targetStart, int targetEnd, String label, String target, boolean image) {}
    public record Problem(Link link, String message) {}
    @FunctionalInterface public interface WebProbe { int status(URI target) throws IOException; }
    public static List<Link> links(String source) {
        String prose = MarkdownSourceMask.prose(source); List<Link> result = new ArrayList<>();
        for (int i = 0; i < prose.length(); i++) {
            if (prose.charAt(i) != '[') continue;
            int labelEnd = bracket(prose, i, '[', ']'); if (labelEnd < 0) break; if (labelEnd + 1 >= prose.length() || prose.charAt(labelEnd + 1) != '(') { i = labelEnd; continue; }
            int end = bracket(prose, labelEnd + 1, '(', ')'); if (end < 0) break;
            int a = labelEnd + 2; while (a < end && Character.isWhitespace(source.charAt(a))) a++;
            int b = a;
            if (a < end && source.charAt(a) == '<') { a++; b = source.indexOf('>', a); if (b < 0 || b > end) continue; }
            else { int depth = 0; for (; b < end; b++) { char ch = source.charAt(b); if (ch == '\\' && b + 1 < end) { b++; continue; } if (Character.isWhitespace(ch) && depth == 0) break; if (ch == '(') depth++; if (ch == ')') depth--; } }
            boolean image = i > 0 && prose.charAt(i - 1) == '!'; result.add(new Link(image ? i - 1 : i, end + 1, a, b, source.substring(i + 1, labelEnd), source.substring(a, b).replace("\\(", "(").replace("\\)", ")"), image)); i = end;
        }
        return List.copyOf(result);
    }
    private static int bracket(String source, int start, char open, char close) { int depth = 0; for (int i = start; i < source.length(); i++) { char ch = source.charAt(i); if (ch == '\\') { i++; continue; } if (ch == open) depth++; else if (ch == close && --depth == 0) return i; } return -1; }
    public static List<Problem> localProblems(String source, Path directory) {
        List<Problem> problems = new ArrayList<>();
        for (Link link : links(source)) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            String target = link.target(); if (target.isBlank() || target.startsWith("#") || target.matches("(?i)^(https?|mailto|tel):.*")) continue;
            if (directory == null && !target.startsWith("file:")) { problems.add(new Problem(link, "Relativer Link kann erst nach dem Speichern des Dokuments geprüft werden.")); continue; }
            try { String clean = target.split("[#?]", 2)[0]; Path path;
                if (clean.startsWith("file:")) path = Path.of(URI.create(clean));
                else { if (clean.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) { problems.add(new Problem(link, "Nicht unterstütztes Linkprotokoll.")); continue; } String decoded = URLDecoder.decode(clean.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8); path = directory.resolve(decoded).normalize(); }
                if (!Files.exists(path)) problems.add(new Problem(link, "Lokales Linkziel fehlt: " + target));
            } catch (IllegalArgumentException failure) { problems.add(new Problem(link, "Ungültiger lokaler Dateipfad: " + target)); }
        }
        return List.copyOf(problems);
    }
    public static List<Problem> webProblems(String source, WebProbe probe) {
        List<Problem> problems = new ArrayList<>(); Map<String, String> cache = new HashMap<>();
        for (Link link : links(source)) {
            if (!link.target().matches("(?i)^https?://.*")) continue;
            if (Thread.currentThread().isInterrupted()) break;
            String message = cache.computeIfAbsent(link.target(), target -> { try { int status = probe.status(URI.create(target)); return status >= 400 ? "HTTP " + status + ": " + target : ""; } catch (IOException | IllegalArgumentException failure) { return "Weblink konnte nicht geprüft werden: " + target + " (" + failure.getMessage() + ")"; } });
            if (!message.isEmpty()) problems.add(new Problem(link, message));
        }
        return List.copyOf(problems);
    }
    /** Explicit action only; finite connection/read timeouts, no redirect to other URI schemes. */
    public static int probe(URI uri) throws IOException {
        if (uri == null || uri.getScheme() == null || !Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Nur HTTP und HTTPS sind erlaubt.");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection(); connection.setConnectTimeout(4000); connection.setReadTimeout(4000); connection.setInstanceFollowRedirects(false); connection.setRequestMethod("HEAD");
        try { return connection.getResponseCode(); } finally { connection.disconnect(); }
    }
    public static String replaceTarget(String source, String oldTarget, String newTarget) {
        if (newTarget.isBlank() || newTarget.indexOf('\n') >= 0 || newTarget.indexOf('\r') >= 0) throw new IllegalArgumentException("Ungültiges Linkziel.");
        String escaped = newTarget.replace(" ", "%20").replace("(", "%28").replace(")", "%29"); StringBuilder result = new StringBuilder(source); List<Link> links = links(source);
        for (int i = links.size() - 1; i >= 0; i--) { Link link = links.get(i); if (link.target().equals(oldTarget)) result.replace(link.targetStart(), link.targetEnd(), escaped); }
        return result.toString();
    }
}
