package de.richmarkdown.io;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class InsertionContentTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T14:07:09Z"), ZoneId.of("Europe/Berlin"));
    @Test void datesUseClockTimezoneAndLocale() {
        assertEquals("2026-11-05", InsertionContent.date(clock, Locale.GERMAN, true));
        assertEquals("5. November 2026", InsertionContent.date(clock, Locale.GERMAN, false));
        assertEquals("November 5, 2026", InsertionContent.date(clock, Locale.US, false));
    }
    @Test void timesHaveOptionalSeconds() {
        assertEquals("15:07", InsertionContent.time(clock, Locale.GERMAN, false));
        assertEquals("15:07:09", InsertionContent.time(clock, Locale.GERMAN, true));
    }
    @Test void unicodeSupportsBmpAndSupplementaryCharacters() {
        assertEquals("©", InsertionContent.codePoint(" U+00A9 "));
        assertEquals("😀", InsertionContent.codePoint("1f600"));
        assertEquals("—", InsertionContent.codePoint("u+2014"));
    }
    @Test void unicodeRejectsControlsSurrogatesOutOfRangeAndMalformedInput() {
        for (String invalid : new String[]{"", "1G", "110000", "D800", "000A", "0000", "1234567", "U+-1"}) {
            assertThrows(IllegalArgumentException.class, () -> InsertionContent.codePoint(invalid), invalid);
        }
    }
}
