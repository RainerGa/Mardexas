package de.richmarkdown.features.checking;

import de.richmarkdown.markdown.MarkdownParser;
import java.nio.file.Path;

/** Maps Markdown source positions to an exported editor paragraph, without modifying the source.
 * Source columns include syntax that has no visible counterpart. Navigation therefore targets the
 * containing rendered paragraph (a table is one paragraph), not a fabricated character column.
 */
public final class IssueNavigation {
    private IssueNavigation() {}

    public static int exportedParagraph(String source, int sourcePosition, Path directory) {
        if (sourcePosition < 0 || sourcePosition > source.length()) throw new IllegalArgumentException("Invalid source position");
        int line = sourcePosition == 0 ? 0 : source.lastIndexOf('\n', sourcePosition - 1) + 1;
        if (line == 0) return 0;
        MarkdownParser parser = new MarkdownParser();
        String full = parser.parseInto(source, directory).getText();
        String prefix = parser.parseInto(source.substring(0, line), directory).getText();
        int position = 0, limit = Math.min(full.length(), prefix.length());
        while (position < limit && full.charAt(position) == prefix.charAt(position)) position++;
        while (position < full.length() && full.charAt(position) == '\n') position++;
        return position == 0 ? 0 : full.lastIndexOf('\n', Math.min(position - 1, full.length() - 1)) + 1;
    }
}
