package de.richmarkdown.io;
import de.richmarkdown.findreplace.FindReplaceEngine;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class SearchPresetStoreTest {
    @TempDir Path profile;
    @Test void storesMultilineUnicodeAndEveryOptionOutsideDocumentAndDefendsCopies() throws Exception {
        var store = new SearchPresetStore(profile); var options = new FindReplaceEngine.FindOptions();
        options.setRegex(true); options.setMatchCase(true); options.setWholeWords(true);
        options.setBackwards(true); options.setSelectionOnly(true); options.setStrikethroughOnly(true);
        options.setFormatFilter(FindReplaceEngine.FormatFilter.BOLD);
        store.save("Recherche", "Ä\n😀", "$1\n", options);
        options.setFormatFilter(FindReplaceEngine.FormatFilter.ANY);
        var preset = new SearchPresetStore(profile).list().get(0);
        assertEquals("Ä\n😀", preset.query()); assertEquals("$1\n", preset.replacement());
        assertTrue(preset.options().isRegex()); assertTrue(preset.options().isSelectionOnly());
        assertTrue(preset.options().isBackwards()); assertTrue(preset.options().isStrikethroughOnly());
        assertTrue(preset.options().isWholeWords()); assertTrue(preset.options().isMatchCase());
        assertEquals(FindReplaceEngine.FormatFilter.BOLD, preset.options().getFormatFilter());
        preset.options().setRegex(false); assertTrue(preset.options().isRegex());
    }
    @Test void sameNameUpdatesAndDeleteAndMissingProfile() throws Exception {
        var store = new SearchPresetStore(profile); assertTrue(store.list().isEmpty());
        store.save("Test", "a", "b", new FindReplaceEngine.FindOptions());
        store.save("test", "c", "d", new FindReplaceEngine.FindOptions());
        assertEquals(1, store.list().size()); assertEquals("c", store.list().get(0).query());
        store.delete(store.list().get(0).id()); assertTrue(store.list().isEmpty());
    }
    @Test void corruptPresetHasClearError() throws Exception {
        var storage = new MarkdownLibraryStore(profile.resolve("search-presets"), MarkdownLibraryStore.Kind.SNIPPET);
        storage.save(null, "broken", "", "not a valid preset");
        assertThrows(IllegalStateException.class, () -> new SearchPresetStore(profile).list());
    }
}
