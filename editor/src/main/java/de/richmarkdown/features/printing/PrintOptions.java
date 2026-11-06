package de.richmarkdown.features.printing;

import java.util.*;
import java.util.prefs.Preferences;

/** User print preferences; never serialized into Markdown. Margins are millimetres. */
public record PrintOptions(double top, double right, double bottom, double left,
                           String fontFamily, double fontSize, String header, String footer,
                           boolean pageNumbers, Scope scope, String pageRanges) {
    public enum Scope { DOCUMENT, SELECTION, CHAPTER }
    public PrintOptions {
        for (double margin : new double[]{top, right, bottom, left}) if (!Double.isFinite(margin) || margin < 0 || margin > 100) throw new IllegalArgumentException("Seitenränder müssen zwischen 0 und 100 mm liegen.");
        Objects.requireNonNull(fontFamily); Objects.requireNonNull(header); Objects.requireNonNull(footer); Objects.requireNonNull(scope); Objects.requireNonNull(pageRanges);
        if (fontFamily.isBlank() || fontFamily.length() > 200 || header.length() > 500 || footer.length() > 500 || header.contains("\n") || footer.contains("\n") || header.contains("\r") || footer.contains("\r")) throw new IllegalArgumentException("Ungültige Drucktexte oder Schriftart.");
        if (!Double.isFinite(fontSize) || fontSize < 6 || fontSize > 48) throw new IllegalArgumentException("Druckschriftgröße muss zwischen 6 und 48 liegen.");
        validateRanges(pageRanges);
    }
    public static PrintOptions defaults() { return new PrintOptions(20, 20, 20, 20, "System", 14, "", "", true, Scope.DOCUMENT, ""); }
    /** The source page order is retained, overlapping ranges do not duplicate pages. */
    public List<Integer> pages(int total) {
        if (total < 1 || total > 1_000_000) throw new IllegalArgumentException("Ungültige Seitenzahl.");
        List<int[]> ranges = validateRanges(pageRanges); BitSet selected = new BitSet(total);
        if (ranges.isEmpty()) selected.set(0, total);
        for (int[] range : ranges) { if (range[0] > total || range[1] > total) throw new IllegalArgumentException("Der Seitenbereich überschreitet die Dokumentlänge (" + total + ")."); selected.set(range[0] - 1, range[1]); }
        return selected.stream().mapToObj(page -> page + 1).toList();
    }
    private static List<int[]> validateRanges(String source) {
        if (source.length() > 2000) throw new IllegalArgumentException("Zu viele Seitenbereiche.");
        List<int[]> result = new ArrayList<>(); if (source.isBlank()) return result;
        for (String part : source.split(",", -1)) { String[] limits = part.strip().split("-", -1); if (limits.length > 2 || limits.length < 1) throw new IllegalArgumentException("Seitenbereiche beispielsweise als 1-3,5 angeben.");
            try { int start = Integer.parseInt(limits[0].strip()); int end = limits.length == 1 ? start : Integer.parseInt(limits[1].strip()); if (start < 1 || end < start || end > 1_000_000) throw new NumberFormatException(); result.add(new int[]{start, end}); }
            catch (NumberFormatException failure) { throw new IllegalArgumentException("Ungültiger Seitenbereich. Beispiel: 1-3,5"); }
        } return result;
    }
    public static double points(double millimetres) { return millimetres * 72 / 25.4; }
    public void save(Preferences prefs) {
        prefs.putDouble("top", top); prefs.putDouble("right", right); prefs.putDouble("bottom", bottom); prefs.putDouble("left", left); prefs.put("font", fontFamily); prefs.putDouble("size", fontSize); prefs.put("header", header); prefs.put("footer", footer); prefs.putBoolean("numbers", pageNumbers);
        // Scope and page ranges belong to the current print operation, never to the next document.
    }
    public static PrintOptions load(Preferences prefs) {
        PrintOptions d = defaults();
        try { return new PrintOptions(prefs.getDouble("top", d.top), prefs.getDouble("right", d.right), prefs.getDouble("bottom", d.bottom), prefs.getDouble("left", d.left), prefs.get("font", d.fontFamily), prefs.getDouble("size", d.fontSize), prefs.get("header", ""), prefs.get("footer", ""), prefs.getBoolean("numbers", true), Scope.DOCUMENT, ""); }
        catch (IllegalArgumentException failure) { return d; }
    }
}
