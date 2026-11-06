package de.richmarkdown.ui;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.features.editing.EditingCommands;
import de.richmarkdown.features.tables.TableTools;
import de.richmarkdown.i18n.I18n;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommandIdentityTest {
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }

    @Test void explicitIdentitiesSurviveTranslationAndReordering() {
        FxTestUtil.runAndWait(() -> {
            String original = I18n.currentLanguageCode();
            try {
                I18n.setCurrentLanguage("de");
                var editor = new MarkdownEditor();
                Menu german = EditingCommands.createMenu(editor, null);
                MenuItem move = german.getItems().stream().filter(item -> "edit.moveParagraphUp".equals(item.getId())).findFirst().orElseThrow();
                MenuBar first = new MenuBar(german);
                CommandIdentity.assign(first);
                assertEquals("feature.menu0.item6", move.getProperties().get(CommandIdentity.LEGACY_ID));
                I18n.setCurrentLanguage("en");
                Menu english = EditingCommands.createMenu(editor, null);
                MenuItem translated = english.getItems().stream().filter(item -> "edit.moveParagraphUp".equals(item.getId())).findFirst().orElseThrow();
                english.getItems().remove(translated); english.getItems().addFirst(translated);
                MenuBar reordered = new MenuBar(new Menu("Other"), english);
                CommandIdentity.assign(reordered);
                assertNotEquals(move.getText(), translated.getText());
                assertEquals(move.getId(), translated.getId());
                assertEquals("feature.menu1.item0", translated.getProperties().get(CommandIdentity.LEGACY_ID));
                assertSame(english, CommandIdentity.menu(reordered, "menu.textParagraphs"));
            } finally { I18n.setCurrentLanguage(original); }
        });
    }

    @Test void previousFeatureIdsBecomeMigrationAliasesAndAreAssignedOnce() {
        FxTestUtil.runAndWait(() -> {
            Menu parent = new Menu("Ansicht");
            MenuItem child = new MenuItem("Schreibansicht"); child.setId("feature.menu2.item15");
            parent.getItems().add(child); MenuBar menus = new MenuBar(parent);
            CommandIdentity.assign(menus);
            assertEquals("menu.view", parent.getId());
            assertEquals("menu.writingView", child.getId());
            assertEquals("feature.menu2.item15", child.getProperties().get(CommandIdentity.LEGACY_ID));
            parent.getItems().addFirst(new MenuItem("Unrelated"));
            CommandIdentity.assign(menus);
            assertEquals("feature.menu2.item15", child.getProperties().get(CommandIdentity.LEGACY_ID));
            assertThrows(IllegalArgumentException.class, () -> CommandIdentity.menu(menus, "menu.missing"));
        });
    }

    @Test void contextRefreshUpdatesEditingAndTablesWithoutOpeningAnyMenu() throws Exception {
        var editor = new MarkdownEditor[1]; var stage = new Stage[1];
        var editing = new Menu[1]; var tables = new Menu[1];
        FxTestUtil.runAndWait(() -> {
            editor[0] = new MarkdownEditor(); stage[0] = new Stage();
            stage[0].setScene(new Scene(new StackPane(editor[0].getControl()), 720, 440)); stage[0].show();
            editing[0] = EditingCommands.createMenu(editor[0], stage[0]);
            tables[0] = TableTools.createMenu(editor[0], () -> stage[0]);
        });
        try {
            FxTestUtil.runAndWait(() -> editor[0].setMarkdown("- List item\n\nText\n"));
            FxTestUtil.flushFxEvents();
            FxTestUtil.runAndWait(() -> {
                ((com.gluonhq.richtextarea.RichTextAreaSkin) editor[0].getControl().getSkin()).getViewModel().setCaretPosition(1);
                refresh(editing[0]); refresh(tables[0]);
                assertFalse(editing[0].isShowing()); assertFalse(tables[0].isShowing());
                assertTrue(item(editing[0], "edit.moveParagraphUp").isDisable());
                assertTrue(item(editing[0], "edit.duplicateParagraph").isDisable());
                assertTrue(item(tables[0], "table.transpose").isDisable());
            });
            FxTestUtil.runAndWait(() -> editor[0].setMarkdown("| A | B |\n| --- | --- |\n| a | b |\n"));
            FxTestUtil.flushFxEvents();
            FxTestUtil.runAndWait(() -> {
                refresh(tables[0]);
                assertFalse(item(tables[0], "table.transpose").isDisable());
                editor[0].getControl().setEditable(false);
                refresh(tables[0]); refresh(editing[0]);
                assertTrue(item(tables[0], "table.transpose").isDisable());
                assertFalse(item(tables[0], "table.exportDelimited").isDisable());
                assertTrue(item(editing[0], "edit.uppercase").isDisable());
            });
        } finally { FxTestUtil.runAndWait(() -> stage[0].close()); }
    }
    private static MenuItem item(Menu menu, String id) {
        return menu.getItems().stream().filter(item -> id.equals(item.getId())).findFirst().orElseThrow();
    }
    private static void refresh(Menu menu) { ((Runnable) menu.getProperties().get(CommandIdentity.REFRESH)).run(); }
}
