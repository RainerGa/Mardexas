package de.richmarkdown.ui;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.GuiTestSupport;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.settings.ApplicationSettings;
import javafx.scene.control.MenuButton;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.*;
import org.testfx.api.FxRobot;
import static org.junit.jupiter.api.Assertions.*;

class RibbonLayoutGuiTest {
    private EditorWindow window;
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @BeforeEach void open() throws Exception {
        FxTestUtil.runAndWait(() -> {
            I18n.setSettingsStore(ApplicationSettings.inMemory()); I18n.setCurrentLanguage("de");
            UiModeService.reset(); I18n.setDocumentStructureEnabled(false);
            window = new EditorWindow(); window.setWidth(1000); window.setHeight(700); window.show();
            window.getCommandSurface().setMode(UiMode.RIBBON);
            window.getEditor().setMarkdown("Some example");
        });
        GuiTestSupport.pulses();
    }
    @AfterEach void close() { FxTestUtil.runAndWait(() -> { window.hide(); UiModeService.reset(); }); }
    @Test void commandsAreAssignedToAllNinePlannedTabs() {
        FxTestUtil.runAndWait(() -> {
            RibbonView ribbon = window.getCommandSurface().getRibbon();
            assertEquals(java.util.List.of("Datei", "Start", "Einfügen", "Dokument", "Prüfen", "Ansicht", "Tabelle", "Fenster", "Hilfe"),
                    ribbon.getTabs().getTabs().stream().map(javafx.scene.control.Tab::getText).toList());
            assertEquals("Start", ribbon.tabTitle("edit.uppercase"));
            assertEquals("Start", ribbon.tabTitle("menu.bold"));
            assertEquals("Dokument", ribbon.tabTitle("tools.writingDashboard"));
            assertEquals("Dokument", ribbon.tabTitle("speech.read"));
            assertEquals("Prüfen", ribbon.tabTitle("links.checkWeb"));
            assertEquals("Prüfen", ribbon.tabTitle("search.folder"));
            assertEquals(window.getCommandRegistry().ids(), ribbon.commandIds());
        });
    }
    @Test void groupCaptionsRemainReadableAndClearOfTheBottomEdgeInEveryTab() throws Exception {
        for (int fontSize : new int[] {13, 16}) {
            FxTestUtil.runAndWait(() -> {
                window.setWidth(1100);
                window.getCommandSurface().getRibbon().setStyle("-fx-font-size: " + fontSize + "px;");
            });
            for (int index = 0; index < 9; index++) {
                final int selected = index;
                FxTestUtil.runAndWait(() -> window.getCommandSurface().getRibbon().getTabs().getSelectionModel().select(selected));
                GuiTestSupport.pulses();
                FxTestUtil.runAndWait(() -> {
                    RibbonView ribbon = window.getCommandSurface().getRibbon();
                    var scroll = (javafx.scene.control.ScrollPane) ribbon.getTabs().getSelectionModel().getSelectedItem().getContent();
                    var viewport = screenBounds(scroll.lookup(".viewport"));
                    var titles = ribbon.lookupAll(".rme-ribbon-group-title").stream().filter(this::actuallyVisible).toList();
                    assertFalse(titles.isEmpty(), "Each register must expose group captions at desktop width");
                    for (var node : titles) {
                        var title = (javafx.scene.control.Label) node;
                        assertTrue(title.getFont().getSize() >= 12, "Captions must use readable typography");
                        assertTrue(screenBounds(title).getMaxY() <= viewport.getMaxY() - 6,
                                "Caption must have breathing room above the editor edge: " + title.getText());
                        assertTrue(title.getHeight() >= title.prefHeight(title.getWidth()) - 0.5,
                                "Caption line must not be vertically compressed: " + title.getText());
                        var text = (javafx.scene.text.Text) title.lookup(".text");
                        assertNotNull(text);
                        assertEquals(title.getText(), text.getText(), "Group captions must be rendered completely");
                    }
                });
            }
        }
    }
    @Test void homeTabUsesAlignedOfficeGroupsWithLargeActionsAndCompactFontControls() throws Exception {
        FxTestUtil.runAndWait(() -> {
            window.setWidth(1280);
            window.getCommandSurface().getRibbon().getTabs().getSelectionModel().select(1);
        });
        GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> {
            RibbonView ribbon = window.getCommandSurface().getRibbon();
            var groups = ribbon.lookupAll(".rme-ribbon-group").stream()
                    .filter(this::actuallyVisible).sorted(java.util.Comparator.comparingDouble(node -> screenBounds(node).getMinX())).toList();
            assertTrue(groups.size() >= 3, "Start must separate clipboard, font and paragraph commands");
            double titleBaseline = Double.NaN;
            double priorRight = Double.NaN;
            for (var group : groups) {
                var bounds = screenBounds(group);
                if (!Double.isNaN(priorRight)) assertTrue(bounds.getMinX() >= priorRight,
                        "Adjacent groups must not overlap");
                priorRight = bounds.getMaxX();
                var title = group.lookup(".rme-ribbon-group-title");
                assertNotNull(title, "Each office group needs a persistent title");
                var titleBounds = screenBounds(title);
                if (Double.isNaN(titleBaseline)) titleBaseline = titleBounds.getMaxY();
                else assertEquals(titleBaseline, titleBounds.getMaxY(), 1.5, "Group titles share a bottom baseline: " + group.getId());
            }
            var commandButtons = ribbon.lookupAll(".rme-ribbon-group-actions").stream()
                    .flatMap(actions -> java.util.stream.Stream.of(".button", ".toggle-button", ".menu-button")
                            .flatMap(selector -> actions.lookupAll(selector).stream()))
                    .distinct().filter(this::actuallyVisible).toList();
            assertTrue(commandButtons.size() >= 10, "The desktop Start tab must expose its frequent commands directly");
            for (int first = 0; first < commandButtons.size(); first++) {
                var firstBounds = screenBounds(commandButtons.get(first));
                for (int second = first + 1; second < commandButtons.size(); second++) {
                    var secondBounds = screenBounds(commandButtons.get(second));
                    double horizontalGap = Math.max(secondBounds.getMinX() - firstBounds.getMaxX(), firstBounds.getMinX() - secondBounds.getMaxX());
                    double verticalGap = Math.max(secondBounds.getMinY() - firstBounds.getMaxY(), firstBounds.getMinY() - secondBounds.getMaxY());
                    assertTrue(horizontalGap >= 1 || verticalGap >= 1,
                            "Commands need a distinct hit area and spacing: " + commandButtons.get(first).getId() + " / " + commandButtons.get(second).getId());
                }
            }
            var large = ribbon.lookupAll(".rme-ribbon-large-command").stream().filter(this::actuallyVisible).toList();
            var small = ribbon.lookupAll(".rme-ribbon-small-command").stream().filter(this::actuallyVisible).toList();
            assertFalse(large.isEmpty(), "Primary commands need a large icon above the label");
            assertFalse(small.isEmpty(), "Secondary commands use compact labeled rows");
            for (var node : large) {
                var button = (javafx.scene.control.ButtonBase) node;
                assertEquals(javafx.scene.control.ContentDisplay.TOP, button.getContentDisplay());
                assertTrue(button.getHeight() >= 64); assertNotNull(button.getGraphic());
            }
            for (String id : java.util.List.of("menu.bold", "menu.italic", "menu.underline", "menu.strikethrough", "menu.inlineCode")) {
                var button = (javafx.scene.control.ToggleButton) ribbon.findControl(id);
                assertNotNull(button); assertTrue(actuallyVisible(button), id);
                assertTrue(button.getStyleClass().contains("rme-ribbon-icon-command"), id);
                assertTrue(button.getWidth() <= 36, "Font toggle should occupy one compact icon cell: " + id);
                assertTrue(button.getHeight() <= 32, id);
                assertNotNull(button.getTooltip()); assertFalse(button.getAccessibleText().isBlank());
            }
        });
    }

    @Test void nativeMouseOnCompactFontToggleFormatsSelectionAndUndoRestoresText() throws Exception {
        FxTestUtil.runAndWait(() -> {
            window.setWidth(1280);
            window.getCommandSurface().getRibbon().getTabs().getSelectionModel().select(1);
            var model = ((com.gluonhq.richtextarea.RichTextAreaSkin) window.getEditorControl().getSkin()).getViewModel();
            model.setSelection(new com.gluonhq.richtextarea.Selection(0, 4));
            window.getEditorControl().requestFocus();
        });
        GuiTestSupport.pulses();
        var bold = FxTestUtil.callAndWait(() -> window.getCommandSurface().getRibbon().findControl("menu.bold"));
        assertTrue(FxTestUtil.callAndWait(() -> actuallyVisible(bold)));
        var robot = new FxRobot(); robot.clickOn(bold); GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> {
            assertTrue(window.getEditor().getMarkdownSource().contains("**Some**"));
            assertTrue(((javafx.scene.control.ToggleButton) bold).isSelected());
            assertTrue(window.getEditorControl().isFocused());
        });
        GuiTestSupport.shortcut(KeyCode.Z, false);
        FxTestUtil.runAndWait(() -> {
            assertFalse(window.getEditor().getMarkdownSource().contains("**"));
            assertFalse(((javafx.scene.control.ToggleButton) bold).isSelected());
        });
    }

    private boolean actuallyVisible(javafx.scene.Node node) {
        if (!node.isManaged()) return false;
        // JavaFX skins position viewports and tab content in unmanaged parents.
        // Visibility is inherited; managed only determines the local layout policy.
        for (var ancestor = node; ancestor != null; ancestor = ancestor.getParent())
            if (!ancestor.isVisible()) return false;
        return node.getScene() != null && node.getScene().getWindow().isShowing();
    }
    private javafx.geometry.Bounds screenBounds(javafx.scene.Node node) {
        return node.localToScene(node.getBoundsInLocal());
    }

    @Test void adaptiveHomeLayoutFitsDesktopAndAllCompactGroupsFitNarrowViewport() throws Exception {
        for (double width : new double[] {1100, 500}) {
            FxTestUtil.runAndWait(() -> {
                window.setWidth(width);
                window.getCommandSurface().getRibbon().getTabs().getSelectionModel().select(1);
            });
            GuiTestSupport.pulses();
            FxTestUtil.runAndWait(() -> {
                RibbonView ribbon = window.getCommandSurface().getRibbon();
                var scroll = (javafx.scene.control.ScrollPane) ribbon.getTabs().getSelectionModel().getSelectedItem().getContent();
                var viewport = screenBounds(scroll.lookup(".viewport"));
                assertTrue(scroll.getContent().getLayoutBounds().getWidth() <= scroll.getViewportBounds().getWidth() + 2,
                        "Home commands must fit without horizontal scrolling at width " + width + ": content=" + scroll.getContent().getLayoutBounds().getWidth() + ", viewport=" + scroll.getViewportBounds().getWidth());
                var groups = ribbon.lookupAll(".rme-ribbon-group").stream().filter(this::actuallyVisible).toList();
                assertEquals(6, groups.size());
                for (var group : groups) {
                    var bounds = screenBounds(group);
                    assertTrue(bounds.getMinX() >= viewport.getMinX() - 1, "Group left edge must be inside viewport: " + group.getId());
                    assertTrue(bounds.getMaxX() <= viewport.getMaxX() + 1, "Group right edge must be inside viewport: " + group.getId());
                    if (width == 500) {
                        var compact = group.lookup(".rme-ribbon-compact-group");
                        assertNotNull(compact); assertTrue(actuallyVisible(compact), "Narrow groups must be compact: " + group.getId());
                    }
                }
                var collapse = ribbon.lookup("#ribbon-collapse");
                assertNotNull(collapse); assertTrue(actuallyVisible(collapse));
                var collapseBounds = screenBounds(collapse);
                var header = screenBounds(ribbon.lookup(".tab-header-area"));
                assertTrue(collapseBounds.getMinY() >= header.getMinY() - 1);
                assertTrue(collapseBounds.getMaxY() <= header.getMaxY() + 1, "Collapse must fit in the header row");
                assertTrue(collapseBounds.getMaxX() <= screenBounds(ribbon).getMaxX() + 1);
            });
        }
    }

    @Test void narrowAndIntermediateCompactGroupsRenderCompleteReadableNames() throws Exception {
        // Shrink the real shown Stage monotonically: Monocle's upload painter
        // otherwise races the native pixel buffer when growing 500 -> 700.
        for (double width : new double[] {700, 500}) {
            FxTestUtil.runAndWait(() -> {
                window.setWidth(width);
                window.getCommandSurface().getRibbon().getTabs().getSelectionModel().select(1);
            });
            GuiTestSupport.pulses();
            FxTestUtil.runAndWait(() -> {
                RibbonView ribbon = window.getCommandSurface().getRibbon();
                var compactGroups = ribbon.lookupAll(".rme-ribbon-compact-group").stream()
                        .filter(this::actuallyVisible).map(MenuButton.class::cast).toList();
                assertFalse(compactGroups.isEmpty(), "Compact groups must be rendered at width " + width);
                var names = new java.util.HashSet<String>();
                for (MenuButton compact : compactGroups) {
                    var labels = compact.lookupAll(".label").stream()
                            .filter(javafx.scene.control.Label.class::isInstance).map(javafx.scene.control.Label.class::cast)
                            .filter(label -> label.isVisible() && !label.getText().isBlank()).toList();
                    assertEquals(1, labels.size(), "Each compact group needs one visible name: " + compact.getId());
                    var label = labels.getFirst();
                    assertTrue(names.add(label.getText()), "Names must distinguish compact groups");
                    var rendered = label.lookupAll(".text").stream().filter(javafx.scene.text.Text.class::isInstance)
                            .map(javafx.scene.text.Text.class::cast).filter(javafx.scene.Node::isVisible).toList();
                    assertFalse(rendered.isEmpty(), "The group name must actually be painted");
                    String paintedText = rendered.stream().map(javafx.scene.text.Text::getText).collect(java.util.stream.Collectors.joining());
                    assertFalse(paintedText.contains("…") || paintedText.contains("..."),
                            "The painted group name must not be truncated: " + compact.getId() + " at " + width + ": " + paintedText);
                    assertEquals(label.getText().replaceAll("\\s", ""), paintedText.replaceAll("\\s", ""),
                            "The complete name must be painted: " + compact.getId());
                    var buttonBounds = screenBounds(compact);
                    for (var text : rendered) {
                        var textBounds = screenBounds(text);
                        assertTrue(textBounds.getMinX() >= buttonBounds.getMinX() - 1,
                                "The painted name must fit left edge: " + compact.getId());
                        assertTrue(textBounds.getMaxX() <= buttonBounds.getMaxX() + 1,
                                "The painted name must fit right edge: " + compact.getId());
                        assertTrue(textBounds.getMaxY() <= buttonBounds.getMaxY() + 1,
                                "Wrapped group names must fit the button height: " + compact.getId());
                    }
                }
            });
        }
    }

    @Test void nativeCollapseButtonStaysInHeaderAndRestoresRibbonWithoutChangingDocument() throws Exception {
        var collapse = FxTestUtil.callAndWait(() -> window.getCommandSurface().getRibbon().lookup("#ribbon-collapse"));
        double originalHeight = FxTestUtil.callAndWait(() -> window.getCommandSurface().getRibbon().getHeight());
        String originalMarkdown = FxTestUtil.callAndWait(() -> window.getEditor().getMarkdownSource());
        var robot = new FxRobot(); robot.clickOn(collapse); GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> {
            assertTrue(window.getCommandSurface().getRibbon().getHeight() < 50, "Collapsed Ribbon occupies only the tab row");
            assertTrue(actuallyVisible(collapse));
            assertEquals(originalMarkdown, window.getEditor().getMarkdownSource());
        });
        robot.clickOn(collapse); GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> {
            assertEquals(originalHeight, window.getCommandSurface().getRibbon().getHeight(), 1);
            assertEquals(originalMarkdown, window.getEditor().getMarkdownSource());
            assertEquals(window.getCommandRegistry().ids(), window.getCommandSurface().getRibbon().commandIds());
        });
    }

    @Test void desktopMoreMenuExecutesAnOverflowCommandAtTheWritingLocation() throws Exception {
        FxTestUtil.runAndWait(() -> {
            window.setWidth(1280);
            window.getCommandSurface().getRibbon().getTabs().getSelectionModel().select(1);
            window.getEditor().goToPosition(3); window.getEditorControl().requestFocus();
        });
        GuiTestSupport.pulses();
        MenuButton more = FxTestUtil.callAndWait(() -> (MenuButton) window.getCommandSurface().getRibbon().lookup("#ribbon-more-font"));
        assertTrue(FxTestUtil.callAndWait(() -> actuallyVisible(more)));
        var robot = new FxRobot(); robot.clickOn(more); GuiTestSupport.pulses();
        var row = FxTestUtil.callAndWait(() -> {
            assertTrue(more.isShowing());
            var item = more.getItems().stream().filter(candidate -> "ribbon-menu-edit.uppercase".equals(candidate.getId())).findFirst().orElseThrow();
            assertFalse(item.isDisable());
            var popupRow = item.getStyleableNode(); assertNotNull(popupRow);
            assertTrue(popupRow.isVisible()); assertTrue(popupRow.getScene().getWindow().isShowing());
            return popupRow;
        });
        robot.clickOn(row); GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> {
            assertFalse(more.isShowing());
            assertTrue(window.getEditor().getDocumentSnapshot().getText().contains("SOME EXAMPLE"));
            assertTrue(window.getEditorControl().isFocused());
        });
        GuiTestSupport.shortcut(KeyCode.Z, false);
        assertTrue(FxTestUtil.callAndWait(() -> window.getEditor().getDocumentSnapshot().getText()).contains("Some example"));
    }

    @Test void nativeMouseCanExecuteACommandThroughCompactGroupPopupAndUndoIt() throws Exception {
        FxTestUtil.runAndWait(() -> {
            window.setWidth(540);
            window.getCommandSurface().getRibbon().getTabs().getSelectionModel().select(1);
            window.getEditor().goToPosition(3); window.getEditorControl().requestFocus();
        });
        GuiTestSupport.pulses();
        MenuButton compact = FxTestUtil.callAndWait(() -> window.getCommandSurface().getRibbon().lookupAll(".rme-ribbon-compact-group").stream()
                .filter(MenuButton.class::isInstance).map(MenuButton.class::cast)
                .filter(button -> "ribbon-compact-font".equals(button.getId())).findFirst().orElseThrow());
        MenuButton group = compact;
        FxTestUtil.runAndWait(() -> { assertTrue(group.isVisible()); assertTrue(group.isManaged()); });
        var robot = new FxRobot(); robot.clickOn(group); GuiTestSupport.pulses();
        assertTrue(FxTestUtil.callAndWait(group::isShowing));
        // Resolve the actual popup row: the compact ribbon also owns an invisible
        // direct button with the same text, so a global text lookup is ambiguous.
        var popupRow = FxTestUtil.callAndWait(() -> {
            var item = group.getItems().stream().filter(candidate -> "ribbon-menu-edit.uppercase".equals(candidate.getId()))
                    .findFirst().orElseThrow();
            assertFalse(item.isDisable());
            var row = item.getStyleableNode();
            assertNotNull(row); assertTrue(row.isVisible());
            assertTrue(row.getScene().getWindow().isShowing());
            return row;
        });
        robot.clickOn(popupRow); GuiTestSupport.pulses();
        assertFalse(FxTestUtil.callAndWait(group::isShowing), "Choosing the command closes its actual popup");
        assertTrue(FxTestUtil.callAndWait(() -> window.getEditor().getDocumentSnapshot().getText()).contains("SOME EXAMPLE"));
        assertTrue(FxTestUtil.callAndWait(() -> window.getEditorControl().isFocused()));
        GuiTestSupport.shortcut(KeyCode.Z, false);
        assertTrue(FxTestUtil.callAndWait(() -> window.getEditor().getDocumentSnapshot().getText()).contains("Some example"));
    }
}
