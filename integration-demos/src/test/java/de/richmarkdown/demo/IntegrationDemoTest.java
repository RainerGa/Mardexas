package de.richmarkdown.demo;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.settings.ApplicationSettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Each external-consumer demo is rendered, edited and disposed in an actual Stage. */
class IntegrationDemoTest {
    private Stage stage;
    private IntegrationDemo.Example example;
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @AfterEach void close() {
        FxTestUtil.runAndWait(() -> {
            if (example != null) example.close();
            if (stage != null) stage.hide();
        });
    }
    private void show(IntegrationDemo.Mode mode) throws Exception {
        FxTestUtil.runAndWait(() -> {
            I18n.setSettingsStore(ApplicationSettings.inMemory());
            example = IntegrationDemo.create(mode);
            stage = new Stage(); stage.setScene(new Scene(example.root(), 1100, 750)); stage.show();
        }); GuiTestSupport.pulses();
    }
    @ParameterizedTest @EnumSource(IntegrationDemo.Mode.class)
    void allDemosRenderRealDocumentsAndDisposeTheirServices(IntegrationDemo.Mode mode) throws Exception {
        show(mode);
        if (mode == IntegrationDemo.Mode.HOST_DIALOG) {
            FxTestUtil.runAndWait(() -> ((Button) example.root().lookup(".button")).fire()); GuiTestSupport.pulses();
        }
        if (mode == IntegrationDemo.Mode.TAB_LOCAL || mode == IntegrationDemo.Mode.TABS_HOST_MENUS) {
            FxTestUtil.runAndWait(() -> ((TabPane) ((BorderPane) example.root()).getCenter()).getSelectionModel().select(1));
            GuiTestSupport.pulses();
        }
        FxTestUtil.runAndWait(() -> {
            assertFalse(example.documents().isEmpty());
            for (var editor : example.documents()) {
                assertTrue(editor.getMarkdownSource().contains("Embedded Mardexas"), mode.toString());
            }
            assertTrue(example.root().snapshot(null, null).getWidth() >= 100);
            for (var pane : example.panes()) {
                assertTrue(pane.getCommands().ids().size() > 100);
                pane.setConfirmationHandler((title, message) -> true);
            }
            assertTrue(example.mayClose());
            List<de.richmarkdown.api.EditorPane> owned = example.panes();
            example.close(); example.close(); owned.forEach(pane -> assertTrue(pane.isClosed()));
            assertTrue(stage.isShowing(), "The library never owns the host lifecycle");
        });
    }
    @Test void closingOneTabDisposesOnlyThatEditorAndKeepsActiveMenuCommands() throws Exception {
        show(IntegrationDemo.Mode.TABS_HOST_MENUS);
        FxTestUtil.runAndWait(() -> {
            var first = example.panes().getFirst(); var second = example.panes().getLast();
            first.setConfirmationHandler((title, message) -> true);
            assertTrue(first.getCommands().execute("tabs.close"));
            assertTrue(first.isClosed()); assertFalse(second.isClosed());
            assertEquals(1, example.panes().size()); assertEquals(1, example.documents().size());
            assertEquals(1, ((TabPane) ((BorderPane) example.root()).getCenter()).getTabs().size());
            assertTrue(second.getCommands().execute("menu.selectAll"));
        }); GuiTestSupport.pulses();
    }
    @Test void generatedReferenceDocumentsEveryRegisteredCommand() throws Exception {
        show(IntegrationDemo.Mode.CLASSIC);
        FxTestUtil.runAndWait(() -> {
            var pane = example.panes().getFirst();
            String reference = IntegrationDemo.commandReference(pane);
            assertEquals(reference, IntegrationDemo.commandReference(pane));
            for (String id : pane.getCommands().ids()) assertTrue(reference.contains("`" + id + "`"), id);
        });
    }
    @Test void readOnlyPreviewCanBeRefreshedWithoutModifyingWritingDocument() throws Exception {
        show(IntegrationDemo.Mode.SPLIT_PREVIEW);
        FxTestUtil.runAndWait(() -> example.panes().getFirst().getEditor().setMarkdown("# Changed chapter\n\nNew text."));
        GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> ((Button) example.root().lookup("#refresh-preview")).fire()); GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> {
            var preview = example.panes().getLast(); assertTrue(preview.isReadOnly());
            assertTrue(preview.getEditor().getMarkdownSource().contains("Changed chapter"));
            assertFalse(preview.getCommands().execute("menu.bold"));
        });
    }
}
