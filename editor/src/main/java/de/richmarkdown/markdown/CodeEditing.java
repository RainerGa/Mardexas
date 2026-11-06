package de.richmarkdown.markdown;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Bounded code operations; syntax colours are a presentation-only preview. */
public final class CodeEditing {
    public static final int MAX_CODE_CHARS = 262_144;
    public static final int MAX_HIGHLIGHT_CHARS = 32_768;
    private static final Pattern TOKENS = Pattern.compile(
            "(?m)//[^\\r\\n]*|#[^\\r\\n]*|/\\*[\\s\\S]*?\\*/|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\b\\d+(?:\\.\\d+)?\\b|\\b[A-Za-z_][A-Za-z_0-9]*\\b");
    private static final Set<String> KEYWORDS = Set.of("public", "private", "protected", "class", "interface", "record",
            "static", "final", "return", "void", "new", "if", "else", "for", "while", "try", "catch", "throw", "throws",
            "import", "package", "true", "false", "null", "int", "long", "boolean", "double", "var", "const", "let",
            "function", "async", "await", "def", "from", "as", "None", "True", "False", "elif", "in", "is", "not",
            "and", "or", "with", "yield", "pass", "select", "where", "join", "order", "by", "insert", "update", "delete");
    public record Span(String text, String kind) { }
    private CodeEditing() { }

    public static String fenced(String code, String language) {
        validate(code);
        if (language == null || language.length() > 120 || language.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Ungültige Codesprache");
        }
        return new MarkdownWriter().write(MarkdownModel.of(List.of(new CodeBlockBlock(language.strip(),
                List.of(code.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1))))));
    }

    public static String indent(String code, int width, boolean remove) {
        validate(code); validateWidth(width);
        String[] lines = code.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].isEmpty()) continue;
            if (!remove) lines[i] = " ".repeat(width) + lines[i];
            else if (lines[i].startsWith("\t")) lines[i] = lines[i].substring(1);
            else {
                int count = 0;
                while (count < width && count < lines[i].length() && lines[i].charAt(count) == ' ') count++;
                lines[i] = lines[i].substring(count);
            }
        }
        return String.join("\n", lines);
    }

    public static String expandTabs(String code, int tabWidth) {
        validate(code); validateWidth(tabWidth);
        StringBuilder result = new StringBuilder(code.length());
        int column = 0;
        for (int i = 0; i < code.length(); i++) {
            char value = code.charAt(i);
            if (value == '\t') {
                int spaces = tabWidth - column % tabWidth;
                result.append(" ".repeat(spaces)); column += spaces;
            } else {
                result.append(value);
                if (value == '\n' || value == '\r') column = 0;
                else column++;
            }
        }
        return result.toString();
    }

    public static String leadingSpacesToTabs(String code, int tabWidth) {
        validate(code); validateWidth(tabWidth);
        String[] lines = code.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            int spaces = 0;
            while (spaces < lines[i].length() && lines[i].charAt(spaces) == ' ') spaces++;
            lines[i] = "\t".repeat(spaces / tabWidth) + " ".repeat(spaces % tabWidth) + lines[i].substring(spaces);
        }
        return String.join("\n", lines);
    }

    public static List<Span> highlight(String code, String language) {
        validate(code);
        if (code.length() > MAX_HIGHLIGHT_CHARS || language == null || language.isBlank()) {
            return List.of(new Span(code, "plain"));
        }
        List<Span> spans = new ArrayList<>();
        var matcher = TOKENS.matcher(code);
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() > end) spans.add(new Span(code.substring(end, matcher.start()), "plain"));
            String token = matcher.group();
            String kind = token.startsWith("//") || token.startsWith("/*") || token.startsWith("#") ? "comment"
                    : token.startsWith("\"") || token.startsWith("'") ? "string"
                    : Character.isDigit(token.charAt(0)) ? "number"
                    : KEYWORDS.contains(token) || KEYWORDS.contains(token.toLowerCase(java.util.Locale.ROOT)) ? "keyword" : "plain";
            spans.add(new Span(token, kind));
            end = matcher.end();
        }
        if (end < code.length()) spans.add(new Span(code.substring(end), "plain"));
        return List.copyOf(spans);
    }

    private static void validate(String code) {
        if (code == null || code.length() > MAX_CODE_CHARS) throw new IllegalArgumentException("Code ist auf 256 KiB Zeichen begrenzt");
    }
    private static void validateWidth(int width) {
        if (width < 1 || width > 16) throw new IllegalArgumentException("Einrückung muss zwischen 1 und 16 liegen");
    }
}
