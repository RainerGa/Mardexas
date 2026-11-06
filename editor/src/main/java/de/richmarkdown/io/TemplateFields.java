package de.richmarkdown.io;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Library-only ${field} syntax is resolved once before inserting ordinary text/Markdown. */
public final class TemplateFields {
    private static final Pattern FIELD = Pattern.compile("\\$\\{([^{}\\r\\n]{1,80})}");
    private static final int MAX_FIELDS = 64;
    private TemplateFields() { }

    public static List<String> names(String template) {
        Objects.requireNonNull(template);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        var matcher = FIELD.matcher(template);
        while (matcher.find()) {
            names.add(matcher.group(1));
            if (names.size() > MAX_FIELDS) throw new IllegalArgumentException("Höchstens 64 Vorlagenfelder zulässig");
        }
        return List.copyOf(names);
    }

    public static String resolve(String template, Map<String, String> values) {
        Objects.requireNonNull(values);
        List<String> missing = new ArrayList<>();
        for (String name : names(template)) if (!values.containsKey(name)) missing.add(name);
        if (!missing.isEmpty()) throw new IllegalArgumentException("Fehlende Vorlagenfelder: " + String.join(", ", missing));
        var matcher = FIELD.matcher(template);
        StringBuilder result = new StringBuilder(template.length());
        int end = 0;
        while (matcher.find()) {
            result.append(template, end, matcher.start()).append(Objects.requireNonNull(values.get(matcher.group(1))));
            end = matcher.end();
            if (result.length() > MarkdownLibraryStore.MAX_CONTENT_BYTES * 4) {
                throw new IllegalArgumentException("Ausgefüllte Vorlage ist zu groß");
            }
        }
        return result.append(template, end, template.length()).toString();
    }
}
