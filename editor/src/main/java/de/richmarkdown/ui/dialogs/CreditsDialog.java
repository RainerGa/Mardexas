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

import de.richmarkdown.i18n.I18n;
import de.richmarkdown.ui.CommandIcons;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Readable, offline attribution windows sharing one complete component inventory. */
public final class CreditsDialog extends Dialog<ButtonType> {
    private static final String SEEN = "acknowledgements.seen";
    private final Label status = text("credits.profileWarning");

    public CreditsDialog(Window owner, boolean thanks) {
        if (owner != null) initOwner(owner);
        setTitle(I18n.text(thanks ? "credits.thanksTitle" : "license.title"));
        setHeaderText(I18n.text(thanks ? "credits.thanksHeader" : "license.header"));
        setResizable(true);
        getDialogPane().setId(thanks ? "thanks-dialog" : "license-dialog");
        getDialogPane().getStylesheets().add(CreditsDialog.class
                .getResource("/de/richmarkdown/ui/credits.css").toExternalForm());
        var screen = javafx.stage.Screen.getPrimary().getVisualBounds();
        getDialogPane().setPrefSize(Math.min(820, screen.getWidth() - 64),
                Math.min(640, screen.getHeight() - 80));
        var close = new ButtonType(I18n.text("dialog.close"), ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().add(close);
        ((Button) getDialogPane().lookupButton(close)).setTooltip(new Tooltip(close.getText()));
        if (thanks) {
            var licenses = new ButtonType(I18n.text("menu.licensing"), ButtonBar.ButtonData.LEFT);
            getDialogPane().getButtonTypes().add(licenses);
            ((Button) getDialogPane().lookupButton(licenses)).setTooltip(new Tooltip(licenses.getText()));
            getDialogPane().lookupButton(licenses).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                event.consume();
                new CreditsDialog(getDialogPane().getScene().getWindow(), false).show();
            });
        }
        TabPane tabs = new TabPane();
        tabs.setId("credits-tabs");
        for (var group : ComponentCredits.Group.values()) {
            if (thanks && group == ComponentCredits.Group.APPLICATION) continue;
            VBox items = new VBox(12);
            items.setPadding(new Insets(16));
            if (group == ComponentCredits.Group.DEVELOPMENT) items.getChildren().add(text("credits.testOnly"));
            for (var component : ComponentCredits.in(group)) {
                VBox card = card(component, thanks);
                items.getChildren().add(card);
                if (!thanks && group == ComponentCredits.Group.ICONS) {
                    VBox.setVgrow(card, javafx.scene.layout.Priority.ALWAYS);
                }
            }
            if (!thanks && group == ComponentCredits.Group.APPLICATION) {
                items.getChildren().addAll(heading("credits.redistribution"), text("credits.redistributionText"));
            }
            ScrollPane scroll = new ScrollPane(items);
            scroll.setFitToWidth(true);
            scroll.setFitToHeight(!thanks && group == ComponentCredits.Group.ICONS);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.setId("credits-scroll-" + group.name());
            Tab tab = new Tab(I18n.text("credits.group." + group.name()), scroll);
            tab.setId("credits-tab-" + group.name());
            tab.setClosable(false);
            tab.setTooltip(new Tooltip(tab.getText()));
            tabs.getTabs().add(tab);
        }
        VBox content = new VBox(12);
        if (thanks) content.getChildren().add(text("credits.thanksText"));
        status.setId("credits-status");
        status.getStyleClass().add("credits-warning");
        status.setVisible(false);
        status.setManaged(false);
        content.getChildren().addAll(tabs, status);
        VBox.setVgrow(tabs, javafx.scene.layout.Priority.ALWAYS);
        getDialogPane().setContent(content);
    }

    /** Only the application entry point calls this; ordinary editor tabs never do. */
    public static CreditsDialog showOnFirstStart(Window owner) {
        boolean profileFailed = false;
        try {
            if (Boolean.parseBoolean(I18n.preference(SEEN, "false"))) return null;
        } catch (UncheckedIOException | SecurityException failure) {
            profileFailed = true;
        }
        CreditsDialog dialog = new CreditsDialog(owner, true);
        dialog.show();
        // Persist only after JavaFX has successfully shown the attribution window.
        try {
            I18n.setPreference(SEEN, "true");
        } catch (UncheckedIOException | SecurityException failure) {
            profileFailed = true;
        }
        if (profileFailed) {
            dialog.status.setManaged(true);
            dialog.status.setVisible(true);
        }
        return dialog;
    }

    private VBox card(ComponentCredits.Component component, boolean thanks) {
        Label name = new Label(component.name() + "  ·  " + component.version());
        name.setWrapText(true);
        name.getStyleClass().add("credits-component-name");
        VBox box = new VBox(8, name);
        box.setId("credit-" + component.id());
        box.getStyleClass().add("credits-card");
        Label creators = new Label(I18n.text("credits.creators") + ": " + component.creators());
        creators.setWrapText(true);
        box.getChildren().add(creators);
        if (!thanks) {
            Label license = new Label(I18n.text("credits.license") + ": " + component.license());
            license.setWrapText(true);
            box.getChildren().add(license);
        }
        box.getChildren().addAll(text("credits.project"), url(component.source(), "credits.project"));
        if (!thanks) box.getChildren().addAll(text("credits.fullLicense"),
                url(component.licenseUrl(), "credits.fullLicense"));
        if (!thanks && component.id().equals("noto")) {
            box.getChildren().add(text("credits.emojiNotice"));
            box.getChildren().add(emojiLicense("Apache-2.0", "LICENSE-NOTO-APACHE-2.0.txt"));
            box.getChildren().add(emojiLicense("Noto region flags", "LICENSE-NOTO-FLAGS.txt"));
            box.getChildren().add(emojiLicense("Noto / emoji-data — NOTICE", "NOTICE.txt"));
        }
        if (!thanks && component.id().equals("emoji-data")) {
            box.getChildren().add(emojiLicense("MIT — emoji-data", "LICENSE-EMOJI-DATA-MIT.txt"));
        }
        if (!thanks && component.id().equals("tabler")) {
            box.getChildren().add(text("credits.iconNotice"));
            TextArea notice = new TextArea(iconLicense());
            notice.setId("tabler-mit-notice");
            notice.setEditable(false);
            notice.setWrapText(true);
            notice.setPrefRowCount(17);
            notice.setMinHeight(140);
            VBox.setVgrow(notice, javafx.scene.layout.Priority.ALWAYS);
            notice.setAccessibleText(I18n.text("credits.fullLicense"));
            box.getChildren().add(notice);
        }
        return box;
    }

    private static javafx.scene.control.TitledPane emojiLicense(String title, String filename) {
        String path = "/com/gluonhq/emoji/noto/" + filename;
        try (var stream = com.gluonhq.emoji.Emoji.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing bundled emoji license: " + filename);
            TextArea notice = new TextArea(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            notice.setEditable(false);
            notice.setWrapText(true);
            notice.setPrefRowCount(10);
            notice.setAccessibleText(title);
            var pane = new javafx.scene.control.TitledPane(title, notice);
            pane.setId("emoji-license-" + filename.replace('.', '-'));
            pane.setExpanded(false);
            return pane;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private TextField url(String value, String labelKey) {
        TextField field = new TextField(value);
        field.setEditable(false);
        field.setAccessibleText(I18n.text(labelKey));
        field.setTooltip(new Tooltip(I18n.text(labelKey) + " — " + I18n.text("credits.copyLink")));
        field.getStyleClass().add("credits-url");
        return field;
    }

    private static Label text(String key) {
        Label label = new Label(I18n.text(key));
        label.setWrapText(true);
        label.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        return label;
    }

    private static Label heading(String key) {
        Label label = text(key);
        label.getStyleClass().add("credits-component-name");
        return label;
    }

    static String iconLicense() {
        try (var stream = CommandIcons.class.getResourceAsStream(CommandIcons.licenseResource())) {
            if (stream == null) throw new IllegalStateException("Missing bundled icon license");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
