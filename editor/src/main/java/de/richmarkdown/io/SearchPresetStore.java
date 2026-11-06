package de.richmarkdown.io;

import de.richmarkdown.findreplace.FindReplaceEngine;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Saved search definitions are application preferences, never document content. */
public final class SearchPresetStore {
    private final MarkdownLibraryStore storage;
    public record Preset(String id, String name, String query, String replacement, FindReplaceEngine.FindOptions options) {
        public Preset { options = options.copy(); }
        @Override public FindReplaceEngine.FindOptions options() { return options.copy(); }
        @Override public String toString() { return name; }
    }
    public SearchPresetStore(Path profile) {
        storage = new MarkdownLibraryStore(profile.resolve("search-presets"), MarkdownLibraryStore.Kind.SNIPPET);
    }
    public List<Preset> list() throws IOException { return storage.list().stream().map(SearchPresetStore::decode).toList(); }
    public void save(String name, String query, String replacement, FindReplaceEngine.FindOptions options) throws IOException {
        var existing = storage.list().stream().filter(entry -> entry.name().equalsIgnoreCase(name.strip())).findFirst();
        String content = encode(query, replacement, options);
        storage.save(existing.map(MarkdownLibraryStore.Entry::id).orElse(null), name, "", content);
    }
    public void delete(String id) throws IOException { storage.delete(id); }
    private static String encode(String query, String replacement, FindReplaceEngine.FindOptions options) {
        String flags = (options.isMatchCase() ? "1" : "0") + (options.isWholeWords() ? "1" : "0")
                + (options.isRegex() ? "1" : "0") + (options.isWildcards() ? "1" : "0")
                + (options.isBackwards() ? "1" : "0") + (options.isSelectionOnly() ? "1" : "0")
                + (options.isStrikethroughOnly() ? "1" : "0");
        return flags + "\n" + options.getFormatFilter().name() + "\n" + query.length() + "\n" + query + replacement;
    }
    private static Preset decode(MarkdownLibraryStore.Entry entry) {
        try {
            String[] parts = entry.content().split("\n", 4);
            if (parts.length != 4 || !parts[0].matches("[01]{7}")) throw new IllegalArgumentException("Invalid preset");
            var options = new FindReplaceEngine.FindOptions();
            options.setMatchCase(parts[0].charAt(0) == '1'); options.setWholeWords(parts[0].charAt(1) == '1');
            options.setRegex(parts[0].charAt(2) == '1'); options.setWildcards(parts[0].charAt(3) == '1');
            options.setBackwards(parts[0].charAt(4) == '1'); options.setSelectionOnly(parts[0].charAt(5) == '1');
            options.setStrikethroughOnly(parts[0].charAt(6) == '1');
            options.setFormatFilter(FindReplaceEngine.FormatFilter.valueOf(parts[1]));
            int queryLength = Integer.parseInt(parts[2]);
            return new Preset(entry.id(), entry.name(), parts[3].substring(0, queryLength), parts[3].substring(queryLength), options);
        } catch (IllegalArgumentException | IndexOutOfBoundsException error) {
            throw new IllegalStateException("Beschädigte Suchvorlage: " + entry.name(), error);
        }
    }
}
