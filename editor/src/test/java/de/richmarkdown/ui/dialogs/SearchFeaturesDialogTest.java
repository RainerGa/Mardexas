package de.richmarkdown.ui.dialogs;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.findreplace.FindReplaceEngine;
import javafx.scene.control.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class SearchFeaturesDialogTest {
    @TempDir Path directory;
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @Test void previewResultSelectionAndTooltips() {
        FxTestUtil.runAndWait(() -> {
            var p = new FindReplaceEngine.ReplacementPreview("old", "new", new FindReplaceEngine.Match(0, 3));
            var dialog = new SearchResultsDialog(null, List.of(p), true, ignored -> { });
            dialog.show();
            assertEquals(1, dialog.getResultConverter().call(ButtonType.OK).size());
            var none = dialog.getDialogPane().lookupAll(".button").stream()
                    .filter(node -> node instanceof Button b && (b.getText().equals("Keine auswählen") || b.getText().equals("Select none")))
                    .map(node -> (Button) node).findFirst().orElseThrow();
            none.fire(); assertTrue(dialog.getResultConverter().call(ButtonType.OK).isEmpty());
            for (var node : dialog.getDialogPane().lookupAll(".button")) assertNotNull(((Button) node).getTooltip());
            dialog.close();
        });
    }
    @Test void folderAsyncResultsPreviewInvalidationAndCancellation() throws Exception {
        Files.writeString(directory.resolve("a.md"), "needle text");
        var dialog = FxTestUtil.callAndWait(() -> {
            var d = new FolderSearchDialog(null, ignored -> { }); d.show();
            d.directory.setText(directory.toString()); d.query.setText("needle"); d.replacement.setText("changed"); d.search(); return d;
        });
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (FxTestUtil.callAndWait(() -> dialog.files.getItems().isEmpty()) && System.nanoTime() < deadline) Thread.sleep(20);
            assertEquals(1, FxTestUtil.callAndWait(() -> dialog.files.getItems().size()));
            while (FxTestUtil.callAndWait(() -> dialog.preview.getText().isEmpty()) && System.nanoTime() < deadline) Thread.sleep(20);
            assertTrue(FxTestUtil.callAndWait(() -> dialog.preview.getText()).contains("changed text"));
            FxTestUtil.runAndWait(() -> {
                dialog.query.setText("different");
                assertTrue(((Button) dialog.getScene().lookup("#folder-search-apply")).isDisabled());
                dialog.cancelWork();
                for (var node : dialog.getScene().getRoot().lookupAll(".button")) {
                    if (node instanceof Button b) assertNotNull(b.getTooltip());
                }
            });
        } finally { FxTestUtil.runAndWait(dialog::close); }
    }
    @Test void folderInvalidDirectoryReportsErrorWithoutBlockingUi() throws Exception {
        var dialog = FxTestUtil.callAndWait(() -> {
            var d = new FolderSearchDialog(null, ignored -> { }); d.show();
            d.directory.setText(directory.resolve("missing").toString()); d.query.setText("x"); d.search(); return d;
        });
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (FxTestUtil.callAndWait(() -> ((Button) dialog.getScene().lookup("#folder-search-start")).isDisabled()) && System.nanoTime() < deadline) Thread.sleep(20);
            assertFalse(FxTestUtil.callAndWait(() -> dialog.status.getText()).isEmpty());
            assertTrue(FxTestUtil.callAndWait(() -> dialog.files.getItems().isEmpty()));
        } finally { FxTestUtil.runAndWait(dialog::close); }
    }
    @Test void changingQueryDiscardsAnAlreadyQueuedPreview() throws Exception {
        Files.writeString(directory.resolve("a.md"), "needle text");
        var dialog = FxTestUtil.callAndWait(() -> {
            var d = new FolderSearchDialog(null, ignored -> { }); d.show();
            d.directory.setText(directory.toString()); d.query.setText("needle"); d.search(); return d;
        });
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (FxTestUtil.callAndWait(() -> dialog.preview.getText().isEmpty()) && System.nanoTime() < deadline) Thread.sleep(20);
            assertFalse(FxTestUtil.callAndWait(() -> dialog.preview.getText().isEmpty()));
            FxTestUtil.runAndWait(() -> {
                // Both changes occur before a worker result can run on the FX thread.
                dialog.replacement.setText("changed");
                dialog.query.setText("different");
            });
            for (int i = 0; i < 10; i++) { Thread.sleep(20); FxTestUtil.flushFxEvents(); }
            FxTestUtil.runAndWait(() -> {
                assertEquals("", dialog.preview.getText());
                assertTrue(((Button) dialog.getScene().lookup("#folder-search-apply")).isDisabled());
                assertFalse(dialog.status.getText().contains("NullPointer"));
            });
        } finally { FxTestUtil.runAndWait(dialog::close); }
    }

}
