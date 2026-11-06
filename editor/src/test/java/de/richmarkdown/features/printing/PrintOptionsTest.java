package de.richmarkdown.features.printing;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.*;

class PrintOptionsTest {
    private PrintOptions ranges(String ranges) { return new PrintOptions(20, 20, 20, 20, "System", 14, "Titel", "Fuß", true, PrintOptions.Scope.DOCUMENT, ranges); }
    @Test void pagesSortedAndDeduplicatedWithOverlaps() { assertEquals(List.of(1, 2, 3, 5), ranges("5,1-3,2").pages(6)); assertEquals(List.of(1,2,3), ranges("").pages(3)); assertThrows(IllegalArgumentException.class, () -> ranges("1-4").pages(3)); }
    @Test void rejectsMalformedAndUnboundedRanges() { for (String value : List.of("0", "-1", "3-2", "1,", "1--2", "1-1000001", "1-", "x", "9999999999999999")) assertThrows(IllegalArgumentException.class, () -> ranges(value), value); }
    @Test void marginsFontAndHeaderValidation() { assertThrows(IllegalArgumentException.class, () -> new PrintOptions(Double.NaN,20,20,20,"System",14,"","",true,PrintOptions.Scope.DOCUMENT,"")); assertThrows(IllegalArgumentException.class, () -> new PrintOptions(20,20,20,20,"System",Double.POSITIVE_INFINITY,"","",true,PrintOptions.Scope.DOCUMENT,"")); assertThrows(IllegalArgumentException.class, () -> new PrintOptions(20,20,20,20,"System",14,"a\nb","",true,PrintOptions.Scope.DOCUMENT,"")); assertEquals(72, PrintOptions.points(25.4), 0.00001); }
    @Test void settingsRoundTripDoesNotPersistDocumentScopeOrRanges() throws Exception { Preferences prefs = Preferences.userRoot().node("/mardexas-test/print-" + UUID.randomUUID()); try { PrintOptions options = new PrintOptions(12,13,14,15,"Serif",16,"Kopf","Fuß",false,PrintOptions.Scope.CHAPTER,"2-4"); options.save(prefs); PrintOptions loaded = PrintOptions.load(prefs); assertEquals(12, loaded.top()); assertEquals("Serif", loaded.fontFamily()); assertEquals("Fuß", loaded.footer()); assertFalse(loaded.pageNumbers()); assertEquals(PrintOptions.Scope.DOCUMENT, loaded.scope()); assertEquals("", loaded.pageRanges()); prefs.putDouble("top", -1); assertEquals(PrintOptions.defaults(), PrintOptions.load(prefs)); } finally { prefs.removeNode(); } }
}
