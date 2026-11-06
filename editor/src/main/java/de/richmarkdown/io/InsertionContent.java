package de.richmarkdown.io;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Objects;

/** Deterministic, locale-aware insertion; values are fixed ordinary text, never live document fields. */
public final class InsertionContent {
    private InsertionContent() { }
    public static String date(Clock clock, Locale locale, boolean iso) {
        return ZonedDateTime.now(Objects.requireNonNull(clock)).format(iso ? DateTimeFormatter.ISO_LOCAL_DATE
                : DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Objects.requireNonNull(locale)));
    }
    public static String time(Clock clock, Locale locale, boolean seconds) {
        return ZonedDateTime.now(Objects.requireNonNull(clock)).format(DateTimeFormatter.ofLocalizedTime(
                seconds ? FormatStyle.MEDIUM : FormatStyle.SHORT).withLocale(Objects.requireNonNull(locale)));
    }
    public static String codePoint(String hexadecimal) {
        Objects.requireNonNull(hexadecimal);
        String value = hexadecimal.strip().replaceFirst("(?i)^U\\+", "");
        if (!value.matches("[0-9a-fA-F]{1,6}")) throw new IllegalArgumentException("Unicode-Codepunkt erwartet, z. B. U+00A9");
        int codePoint = Integer.parseInt(value, 16);
        if (!Character.isValidCodePoint(codePoint) || codePoint >= 0xD800 && codePoint <= 0xDFFF
                || Character.isISOControl(codePoint)) throw new IllegalArgumentException("Kein darstellbares Unicode-Zeichen");
        return new String(Character.toChars(codePoint));
    }
}
