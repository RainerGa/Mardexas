/*
 * Copyright (C) 2026 Rainer Garbotz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.richmarkdown.ui.dialogs;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.features.printing.PrintOptions;
import de.richmarkdown.features.printing.PrintOptionsDialog;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.settings.ApplicationSettings;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for visible input, persistence, locale and theme handling. */
class DialogProfessionalizationTest {
    @TempDir Path directory;

    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @BeforeEach void settings() {
        I18n.setSettingsStore(new ApplicationSettings(directory));
        I18n.setCurrentLanguage("en");
    }
    @AfterEach void reset() {
        I18n.setSettingsStore(ApplicationSettings.inMemory());
        I18n.setCurrentLanguage("de");
    }

    private static Button ok(Dialog<?> dialog) {
        return (Button) dialog.getDialogPane().lookupButton(DialogButtons.OK);
    }

    @Test void requiredLinkImageAndCodeInputsDisableConfirmation() {
        FxTestUtil.runAndWait(() -> {
            var link = new LinkDialog();
            var image = new ImageDialog();
            var code = new CodeBlockDialog();
            assertTrue(ok(link).isDisabled());
            assertTrue(ok(image).isDisabled());
            assertTrue(ok(code).isDisabled());
            link.getUrlField().setText("https://example.org");
            image.getUrlField().setText("images/a.png");
            code.getCodeArea().setText("return 1;");
            assertFalse(ok(link).isDisabled());
            assertFalse(ok(image).isDisabled());
            assertFalse(ok(code).isDisabled());
            assertEquals("return 1;", code.getResultConverter().call(DialogButtons.OK));
            link.getUrlField().setText(" ");
            assertTrue(ok(link).isDisabled());
            assertTrue(link.getDialogPane().lookupAll(".rme-dialog-error").stream().anyMatch(javafx.scene.Node::isVisible));
            assertNull(link.getResultConverter().call(DialogButtons.CANCEL));
        });
    }

    @Test void tableValidatesVisibleUncommittedTextAndPersistsOnlyConfirmation() {
        FxTestUtil.runAndWait(() -> {
            var table = new TableDialog();
            for (String invalid : List.of("", "abc", "0", "21", "2.5")) {
                table.getRowsSpinner().getEditor().setText(invalid);
                assertTrue(ok(table).isDisabled(), invalid);
            }
            table.getRowsSpinner().getEditor().setText("7");
            table.getColumnsSpinner().getEditor().setText("4");
            assertFalse(ok(table).isDisabled());
            assertNull(table.getResultConverter().call(DialogButtons.CANCEL));
            assertNull(I18n.preference("table.lastRows", null));
            assertEquals(7, table.getResultConverter().call(DialogButtons.OK).rows());
            assertEquals(7, new TableDialog().getRowsSpinner().getValue());
            assertEquals(4, new TableDialog().getColumnsSpinner().getValue());
        });
    }

    @Test void tableRestoresFromNewSettingsInstanceAndRejectsDamagedValues() {
        I18n.setSettingsStore(new ApplicationSettings(directory));
        FxTestUtil.runAndWait(() -> {
            var table = new TableDialog();
            table.getRowsSpinner().getEditor().setText("9");
            table.getResultConverter().call(DialogButtons.OK);
        });
        I18n.setSettingsStore(new ApplicationSettings(directory));
        FxTestUtil.runAndWait(() -> assertEquals(9, new TableDialog().getRowsSpinner().getValue()));
        I18n.setPreference("table.lastRows", "2147483648");
        I18n.setPreference("table.lastColumns", "0");
        FxTestUtil.runAndWait(() -> {
            var table = new TableDialog();
            assertEquals(3, table.getRowsSpinner().getValue());
            assertEquals(3, table.getColumnsSpinner().getValue());
        });
    }

    @Test void templateFieldsRequireEveryValueAndReturnResolvedContent() {
        FxTestUtil.runAndWait(() -> {
            var dialog = new TemplateFieldsDialog(null, "# ${title}\n${name}");
            assertTrue(ok(dialog).isDisabled());
            var content = ((javafx.scene.control.ScrollPane) dialog.getDialogPane().getContent()).getContent();
            assertTrue(content.getStyleClass().contains("rme-dialog-section"));
            assertTrue(content.lookupAll(".label").stream().map(Label.class::cast)
                    .anyMatch(label -> label.getLabelFor() == content.lookup("#template-field-0")));
            ((TextField) ((javafx.scene.control.ScrollPane) dialog.getDialogPane().getContent()).getContent().lookup("#template-field-0")).setText("Title");
            assertTrue(ok(dialog).isDisabled());
            ((TextField) ((javafx.scene.control.ScrollPane) dialog.getDialogPane().getContent()).getContent().lookup("#template-field-1")).setText("Name");
            assertFalse(ok(dialog).isDisabled());
            assertEquals("# Title\nName", dialog.getResultConverter().call(DialogButtons.OK));
            assertNull(dialog.getResultConverter().call(DialogButtons.CANCEL));
        });
    }

    @Test void printPersistsChosenValuesAfterConfirmationAndRestoresPaperWithoutEnumCast() {
        I18n.setSettingsStore(new ApplicationSettings(directory));
        FxTestUtil.runAndWait(() -> {
            var print = new PrintDialog(List.of(), null);
            print.getCopiesSpinner().getEditor().setText("6");
            print.getOrientationBox().setValue(PageOrientation.LANDSCAPE);
            print.getPaperBox().setValue(Paper.A5);
            assertNull(print.getResultConverter().call(DialogButtons.CANCEL));
            assertNull(I18n.preference("print.lastCopies", null));
            assertEquals(6, print.getResultConverter().call(DialogButtons.PRINT).copies());
        });
        I18n.setSettingsStore(new ApplicationSettings(directory));
        FxTestUtil.runAndWait(() -> {
            var print = new PrintDialog(List.of(), null);
            assertEquals(6, print.getCopiesSpinner().getValue());
            assertEquals(Paper.A5, print.getPaperBox().getValue());
            assertEquals(PageOrientation.LANDSCAPE, print.getOrientationBox().getValue());
            assertTrue(print.getDialogPane().lookupButton(DialogButtons.PRINT).isDisabled());
        });
    }

    @Test void printOptionsExposeAllFieldsAndValidateLive() {
        FxTestUtil.runAndWait(() -> {
            var dialog = new PrintOptionsDialog(null, PrintOptions.defaults(), true);
            var fields = dialog.getDialogPane().lookupAll(".text-field").stream().map(TextField.class::cast).toList();
            assertEquals(9, fields.size());
            TextField margin = fields.stream().filter(f -> f.getText().equals("20.0")).findFirst().orElseThrow();
            for (String invalid : List.of("NaN", "Infinity", "-1", "101", "x")) {
                margin.setText(invalid);
                assertTrue(ok(dialog).isDisabled(), invalid);
            }
            margin.setText("15,5");
            assertFalse(ok(dialog).isDisabled());
            var result = dialog.getResultConverter().call(DialogButtons.OK);
            assertTrue(List.of(result.top(), result.right(), result.bottom(), result.left()).contains(15.5));
            var restored = new PrintOptionsDialog(null, PrintOptions.defaults(), true);
            assertFalse(ok(restored).isDisabled());
            var again = restored.getResultConverter().call(DialogButtons.OK);
            assertEquals(result, again);
        });
    }

    @Test void printOptionsInvalidSavedStateFallsBackAndCancelDoesNotWrite() {
        I18n.setPreference("printOptions.top", "NaN");
        I18n.setPreference("printOptions.scope", "removed");
        FxTestUtil.runAndWait(() -> {
            var dialog = new PrintOptionsDialog(null, PrintOptions.defaults(), false);
            assertFalse(ok(dialog).isDisabled());
            assertNull(dialog.getResultConverter().call(DialogButtons.CANCEL));
            assertEquals("NaN", I18n.preference("printOptions.top", ""));
        });
    }

    @Test void templateDirectoryOverridesBuiltinsAndSortsAdditionalFiles() throws Exception {
        Files.writeString(directory.resolve("article.md"), "# Override");
        Files.writeString(directory.resolve("z.md"), "# Last");
        Files.writeString(directory.resolve("a.md"), "# First extra");
        Files.writeString(directory.resolve("ignored.txt"), "ignored");
        Files.createDirectory(directory.resolve("directory.md"));
        I18n.setPreference("templates.directory", directory.toString());
        FxTestUtil.runAndWait(() -> {
            var dialog = new TemplateDialog(null);
            dialog.show();
            try {
                var list = (ListView<?>) dialog.getDialogPane().lookup(".list-view");
                assertEquals(6, list.getItems().size());
                assertEquals("# Override", dialog.getResultConverter().call(DialogButtons.OK));
                list.getSelectionModel().select(4);
                assertEquals("# First extra", dialog.getResultConverter().call(DialogButtons.OK));
                list.getSelectionModel().select(5);
                assertEquals("# Last", dialog.getResultConverter().call(DialogButtons.OK));
                assertNull(dialog.getResultConverter().call(DialogButtons.CANCEL));
                list.getSelectionModel().clearSelection();
                assertTrue(ok(dialog).isDisabled());
            } finally { dialog.close(); }
        });
    }

    @Test void invalidTemplateDirectoriesFallBackToFourBundledTemplates() {
        for (String path : List.of("", directory.resolve("missing").toString(), "bad\u0000path", directory.toString())) {
            I18n.setPreference("templates.directory", path);
            FxTestUtil.runAndWait(() -> {
                var dialog = new TemplateDialog(null);
                dialog.show();
                try {
                    var list = (ListView<?>) dialog.getDialogPane().lookup(".list-view");
                    assertEquals(4, list.getItems().size());
                    assertFalse(ok(dialog).isDisabled());
                } finally { dialog.close(); }
            });
        }
    }

    @Test void allLocalesConstructDialogsAndUseCurrentButtonLanguage() {
        for (String language : List.of("de", "en", "fr", "es", "pt-BR", "ja", "zh-CN", "it", "ko", "nl")) {
            I18n.setCurrentLanguage(language);
            FxTestUtil.runAndWait(() -> {
                for (Dialog<?> dialog : List.of(new LinkDialog(), new ImageDialog(), new TableDialog(),
                        new CodeBlockDialog(), new TemplateFieldsDialog(null, "${name}"),
                        new TemplateDialog(null), new PrintDialog(List.of(), null),
                        new PrintOptionsDialog(null, PrintOptions.defaults(), false))) {
                    assertTrue(dialog.getDialogPane().getStylesheets().stream().anyMatch(s -> s.endsWith("dialogs.css")));
                    if (dialog.getDialogPane().getButtonTypes().contains(DialogButtons.OK)) {
                        assertEquals(I18n.text("dialog.ok"), ok(dialog).getAccessibleText());
                        assertTrue(ok(dialog).getStyleClass().contains("rme-dialog-primary"));
                    }
                }
            });
        }
    }

    @Test void everyLocaleContainsAllEnglishResourceKeys() throws Exception {
        var english = new java.util.Properties();
        try (var input = getClass().getResourceAsStream("/de/richmarkdown/i18n/messages_en.properties")) {
            assertNotNull(input);
            english.load(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8));
        }
        for (String locale : List.of("de", "en", "fr", "es", "pt_BR", "ja", "zh_CN", "it", "ko", "nl")) {
            var properties = new java.util.Properties();
            try (var input = getClass().getResourceAsStream("/de/richmarkdown/i18n/messages_" + locale + ".properties")) {
                assertNotNull(input);
                properties.load(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8));
            }
            var missing = new java.util.TreeSet<>(english.stringPropertyNames());
            missing.removeAll(properties.stringPropertyNames());
            assertTrue(missing.isEmpty(), locale + ": " + missing);
        }
    }

    @Test void darkAndContrastThemesReachSeparateDialogRoots() {
        for (Map.Entry<String, String> setting : Map.of("access.dark", "rme-dark", "access.contrast", "rme-high-contrast").entrySet()) {
            I18n.setPreference("access.dark", "false");
            I18n.setPreference("access.contrast", "false");
            I18n.setPreference(setting.getKey(), "true");
            FxTestUtil.runAndWait(() -> {
                var dialog = new LinkDialog();
                assertTrue(dialog.getDialogPane().getStyleClass().contains(setting.getValue()));
                var label = dialog.getDialogPane().lookupAll(".label").stream().map(Label.class::cast)
                        .filter(l -> l.getLabelFor() == dialog.getUrlField()).findFirst();
                assertTrue(label.isPresent());
            });
        }
    }
}
