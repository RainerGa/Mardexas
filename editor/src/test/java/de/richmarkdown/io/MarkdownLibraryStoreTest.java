package de.richmarkdown.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownLibraryStoreTest {
    @TempDir Path root;

    @Test void roundTripsUnicodeMarkdownAndShortcutWithoutDocumentMetadata() throws Exception {
        var store = new MarkdownLibraryStore(root, MarkdownLibraryStore.Kind.SNIPPET);
        assertEquals(List.of(), store.list());
        String markdown = "## Grüße\n\n| A | B |\n|---|---|\n| 😀 | ${Name} |\n";
        var entry = store.save(null, "Grüße", ";gruss", markdown);
        var reloaded = new MarkdownLibraryStore(root, MarkdownLibraryStore.Kind.SNIPPET).list().getFirst();
        assertEquals(entry, reloaded);
        assertEquals(markdown, reloaded.content());
        assertThrows(UnsupportedOperationException.class, () -> store.list().clear());
    }

    @Test void updateRenameAndDeleteDoNotLeaveTemporaryFiles() throws Exception {
        var store = new MarkdownLibraryStore(root, MarkdownLibraryStore.Kind.TEMPLATE);
        var original = store.save(null, "Alt", "", "# Alt");
        var updated = store.save(original.id(), "Neu", "", "# Neu");
        assertEquals(List.of(updated), store.list());
        try (var files = Files.list(root.resolve("template"))) { assertEquals(1, files.count()); }
        store.delete(original.id());
        assertEquals(List.of(), store.list());
        store.delete(original.id());
    }

    @Test void namesAndShortcutsAreUniqueAndKindsIndependent() throws Exception {
        var snippets = new MarkdownLibraryStore(root, MarkdownLibraryStore.Kind.SNIPPET);
        snippets.save(null, "Name", ";a", "a");
        assertThrows(IOException.class, () -> snippets.save(null, "NAME", ";b", "b"));
        assertThrows(IOException.class, () -> snippets.save(null, "Other", ";a", "b"));
        var templates = new MarkdownLibraryStore(root, MarkdownLibraryStore.Kind.TEMPLATE);
        templates.save(null, "Name", "", "template");
        assertEquals(1, templates.list().size());
        assertEquals("a", snippets.list().getFirst().content());
    }

    @Test void rejectsTraversalInvalidFieldsOversizeAndCorruptEntries() throws Exception {
        var store = new MarkdownLibraryStore(root, MarkdownLibraryStore.Kind.SNIPPET);
        assertThrows(IllegalArgumentException.class, () -> store.delete("../outside"));
        assertThrows(IllegalArgumentException.class, () -> store.save(null, " ", "", "a"));
        assertThrows(IllegalArgumentException.class, () -> store.save(null, "A", "bad shortcut", "a"));
        assertThrows(IllegalArgumentException.class, () -> store.save(null, "A", "", "x".repeat(MarkdownLibraryStore.MAX_CONTENT_BYTES + 1)));
        Files.createDirectories(root.resolve("snippet"));
        Files.writeString(root.resolve("snippet/bad.properties"), "name=Corrupt");
        assertThrows(IOException.class, store::list);
    }

    @Test void symlinkedEntryAndLibraryCannotRedirectWrites() throws Exception {
        Path outside = Files.createDirectory(root.resolve("outside"));
        Path config = Files.createDirectory(root.resolve("config"));
        Files.createSymbolicLink(config.resolve("snippet"), outside);
        var redirected = new MarkdownLibraryStore(config, MarkdownLibraryStore.Kind.SNIPPET);
        assertThrows(IOException.class, () -> redirected.save(null, "A", "", "bad"));
        var store = new MarkdownLibraryStore(root, MarkdownLibraryStore.Kind.TEMPLATE);
        var entry = store.save(null, "A", "", "safe");
        Path file = root.resolve("template").resolve(entry.id() + ".properties");
        Files.delete(file);
        Path target = root.resolve("outside.md");
        Files.writeString(target, "unchanged");
        Files.createSymbolicLink(file, target);
        assertThrows(IOException.class, store::list);
        assertThrows(IOException.class, () -> store.delete(entry.id()));
        assertEquals("unchanged", Files.readString(target));
    }

    @Test void importsMarkdownAndRejectsOversizedOrNonFileInput() throws Exception {
        Path markdown = root.resolve("source.md");
        Files.writeString(markdown, "# Ä\n\n**B**");
        assertEquals("# Ä\n\n**B**", MarkdownLibraryStore.importMarkdown(markdown));
        assertThrows(IOException.class, () -> MarkdownLibraryStore.importMarkdown(root));
        Files.writeString(markdown, "x".repeat(MarkdownLibraryStore.MAX_CONTENT_BYTES + 1));
        assertThrows(IOException.class, () -> MarkdownLibraryStore.importMarkdown(markdown));
    }

    @Test void entryLimitBoundsResourceUse() throws Exception {
        var store = new MarkdownLibraryStore(root, MarkdownLibraryStore.Kind.SNIPPET);
        for (int i = 0; i < MarkdownLibraryStore.MAX_ENTRIES; i++) store.save(null, "Entry " + i, "", "x");
        assertThrows(IOException.class, () -> store.save(null, "Too many", "", "x"));
        var first = store.list().getFirst();
        store.save(first.id(), first.name(), "", "updated");
        assertEquals(MarkdownLibraryStore.MAX_ENTRIES, store.list().size());
    }

    @Test void ioErrorsArePropagated() throws Exception {
        Path file = root.resolve("file");
        Files.writeString(file, "x");
        var store = new MarkdownLibraryStore(file, MarkdownLibraryStore.Kind.SNIPPET);
        assertThrows(IOException.class, () -> store.save(null, "Entry", "", "x"));
    }
}
