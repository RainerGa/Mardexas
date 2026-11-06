package de.richmarkdown.ui;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.GuiTestSupport;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.io.WorkspaceSession;
import de.richmarkdown.settings.ApplicationSettings;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.Node;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Resize the actual tab host, rather than the detached per-document Stage. */
class WorkspaceResizeGuiTest {
    @TempDir Path directory;
    private WorkspaceWindow workspace;
    private EditorWindow editor;
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @BeforeEach void open() throws Exception {
        FxTestUtil.runAndWait(() -> {
            I18n.setSettingsStore(ApplicationSettings.inMemory());
            I18n.setCurrentLanguage("de"); UiModeService.reset();
            workspace = new WorkspaceWindow(new WorkspaceSession(directory.resolve("session.properties")));
            workspace.newDocument(); editor = workspace.newDocument();
            workspace.setWidth(1200); workspace.setHeight(850); workspace.show();
            editor.getEditor().setMarkdown("# Resize\n\nText remains in the active document.");
        });
        GuiTestSupport.pulses();
    }
    @AfterEach void close() {
        FxTestUtil.runAndWait(() -> { workspace.dispose(); workspace.hide(); UiModeService.reset(); });
    }

    @ParameterizedTest @EnumSource(UiMode.class)
    void verticalResizeKeepsDocumentTabsAndChromeAtTheSamePosition(UiMode mode) throws Exception {
        FxTestUtil.runAndWait(() -> editor.getCommandSurface().setMode(mode));
        GuiTestSupport.pulses();
        double chromeY = FxTestUtil.callAndWait(() -> editor.getChrome().localToScene(0, 0).getY());
        double chromeHeight = FxTestUtil.callAndWait(() -> editor.getChrome().getHeight());
        AtomicInteger presentationChanges = observeRibbonPresentation();
        // Monocle 21 mutates reused pixel-buffer limits on shrink/grow. The
        // native GTK run also covers growth in the same shown window.
        double[] heights = "Monocle".equals(System.getProperty("glass.platform"))
                ? new double[] {810, 700, 560, 500, 430}
                : new double[] {810, 700, 560, 430, 500, 650, 790};
        for (double height : heights) {
            FxTestUtil.runAndWait(() -> workspace.setHeight(height));
            GuiTestSupport.pulses();
            FxTestUtil.runAndWait(() -> {
                assertEquals(height, workspace.getHeight(), 1);
                assertEquals(chromeY, editor.getChrome().localToScene(0, 0).getY(), .5);
                assertEquals(chromeHeight, editor.getChrome().getHeight(), .5);
                TabPane documents = (TabPane) workspace.getScene().lookup(".tab-pane");
                assertEquals(2, documents.getTabs().size());
                var captions = documents.lookupAll(".tab-header-area .tab-label").stream()
                        .filter(node -> node.localToScene(0, 0).getY() < chromeY).toList();
                assertEquals(2, captions.size(), "Both document captions must remain rendered");
                for (Node caption : captions) {
                    assertTrue(caption.isVisible());
                    assertTrue(caption.getBoundsInLocal().getHeight() > 0);
                    assertTrue(caption.getBoundsInLocal().getWidth() > 0);
                }
                Region root = (Region) workspace.getScene().getRoot();
                assertEquals(workspace.getScene().getHeight(), root.getHeight(), .5);
                assertEquals(workspace.getScene().getWidth(), root.getWidth(), .5);
                assertSame(editor, workspace.activeDocument());
                assertEquals("Resize\n\nText remains in the active document.", editor.getEditor().getDocumentSnapshot().getText());
            });
        }
        assertEquals(0, presentationChanges.get(), "Height changes must not toggle ribbon groups");
    }

    @ParameterizedTest @EnumSource(UiMode.class)
    void widthChangesWithinTheCompactRangeDoNotToggleGroups(UiMode mode) throws Exception {
        FxTestUtil.runAndWait(() -> { editor.getCommandSurface().setMode(mode); workspace.setWidth(550); });
        GuiTestSupport.pulses();
        AtomicInteger presentationChanges = observeRibbonPresentation();
        double[] widths = "Monocle".equals(System.getProperty("glass.platform"))
                ? new double[] {545, 540, 535, 530, 525}
                : new double[] {540, 530, 520, 535, 550, 560};
        for (double width : widths) {
            FxTestUtil.runAndWait(() -> workspace.setWidth(width));
            GuiTestSupport.pulses();
            FxTestUtil.runAndWait(() -> {
                assertEquals(width, workspace.getWidth(), 1);
                assertEquals(width, workspace.getScene().getWidth(), 1);
            });
        }
        assertEquals(0, presentationChanges.get(), "Compact groups must not be expanded and collapsed during each layout");
    }

    private AtomicInteger observeRibbonPresentation() {
        AtomicInteger changes = new AtomicInteger();
        FxTestUtil.runAndWait(() -> {
            for (Node node : editor.getCommandSurface().getRibbon().lookupAll(".rme-ribbon-group-actions")) {
                node.visibleProperty().addListener((obs, old, value) -> changes.incrementAndGet());
                node.managedProperty().addListener((obs, old, value) -> changes.incrementAndGet());
            }
        });
        return changes;
    }
}
