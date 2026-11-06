/*
 * Copyright (C) 2026 Rainer Garbotz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package de.richmarkdown.ui;

import de.richmarkdown.FxTestUtil;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Source, vector geometry and transparent rendering checks for the whole catalog. */
class CommandIconsTest {
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }

    @Test void originalArtworkAndCompleteLicenseMatchReviewedUpstreamBytes() throws Exception {
        java.util.Properties hashes = new java.util.Properties();
        String prefix = "/de/richmarkdown/ui/icons/tabler/";
        try (var input = CommandIcons.class.getResourceAsStream(prefix + "upstream-sha256.properties")) {
            assertNotNull(input); hashes.load(input);
        }
        assertEquals(144, hashes.size(), "143 upstream SVGs and their original license");
        assertEquals("d5a3f948ae5cb95bfc4d2f767ef2bf6a778815b0ddb2d1c48037744cf636518e", hashes.getProperty("LICENSE"));
        for (String name : hashes.stringPropertyNames()) {
            try (var input = CommandIcons.class.getResourceAsStream(prefix + name)) {
                assertNotNull(input, name);
                assertEquals(hashes.getProperty(name), java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())), name);
            }
        }
        for (String id : CommandIcons.supportedCommandIds()) {
            for (String source : CommandIcons.sourceNames(id)) assertTrue(hashes.containsKey(source + ".svg"), id);
        }
    }

    @Test void everySourceAndLicenseIsIncludedInRuntimeResources() throws IOException {
        assertEquals("Tabler Icons v3.34.1", CommandIcons.sourceRelease());
        try (var input = CommandIcons.class.getResourceAsStream(CommandIcons.licenseResource())) {
            assertNotNull(input);
            String license = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(license.contains("MIT License"));
            assertTrue(license.contains("Paweł Kuna"));
            assertTrue(license.contains("Permission is hereby granted"));
        }
        for (String id : CommandIcons.supportedCommandIds()) {
            for (String source : CommandIcons.sourceNames(id)) {
                try (var original = CommandIcons.class.getResourceAsStream("/de/richmarkdown/ui/icons/tabler/" + source + ".svg")) {
                    assertNotNull(original, id + " source " + source);
                    assertTrue(new String(original.readAllBytes(), StandardCharsets.UTF_8).contains("<svg"));
                }
            }
        }
        assertThrows(UnsupportedOperationException.class, () -> CommandIcons.supportedCommandIds().clear());
    }

    @Test void eachCommandHasFreshThemedVectorNodesWithoutTextOrImagePlaceholders() {
        FxTestUtil.runAndWait(() -> {
            for (String id : CommandIcons.supportedCommandIds()) {
                Node first = CommandIcons.create(id, 18), second = CommandIcons.create(id, 18);
                assertNotSame(first, second, id);
                assertEquals(0, ToolbarIcons.countOf(first, javafx.scene.text.Text.class), id);
                assertEquals(0, ToolbarIcons.countOf(first, javafx.scene.image.ImageView.class), id);
                assertTrue(ToolbarIcons.countOf(first, SVGPath.class) > 0, id);
                assertEquals(18, first.prefWidth(-1), .01, id);
                assertEquals(18, first.prefHeight(-1), .01, id);
                assertTrue(first.isMouseTransparent(), id);
                assertEquals(CommandIcons.signature(id), CommandIcons.signature(id));
            }
        });
    }

    @Test void renderedMotifsAreUniqueAmongActualCommandsAndVariants() {
        Set<String> signatures = new HashSet<>();
        for (String id : CommandIcons.supportedCommandIds()) {
            assertTrue(signatures.add(CommandIcons.signature(id)), "Duplicate icon geometry: " + id);
        }
        for (int i = 1; i <= 10; i++) {
            String id = "menu.recentFiles." + i;
            assertTrue(CommandIcons.supports(id));
            assertTrue(signatures.add(CommandIcons.signature(id)), id);
        }
        assertNotEquals(CommandIcons.signature("menu.heading1"), CommandIcons.signature("menu.heading2"));
        assertNotEquals(CommandIcons.signature("menu.save"), CommandIcons.signature("menu.saveAs"));
    }

    @Test void everyIconActuallyPaintsForegroundOnTransparentBackgroundAtMenuAndRibbonScales() {
        FxTestUtil.runAndWait(() -> {
            Stage stage = new Stage();
            StackPane root = new StackPane();
            root.setStyle("-fx-background-color: transparent; -fx-text-base-color: #24354b;");
            stage.setScene(new Scene(root, 90, 90, Color.TRANSPARENT)); stage.show();
            try {
                java.util.Map<Double, Set<List<Integer>>> rendered = new java.util.HashMap<>();
                for (String id : CommandIcons.supportedCommandIds()) {
                    for (double size : List.of(16.0, 24.0, 32.0, 48.0)) {
                        Node icon = CommandIcons.create(id, size);
                        root.getChildren().setAll(icon); root.applyCss(); root.layout();
                        SnapshotParameters parameters = new SnapshotParameters(); parameters.setFill(Color.TRANSPARENT);
                        WritableImage snapshot = icon.snapshot(parameters, null);
                        int foreground = 0, transparent = 0;
                        List<Integer> pixels = new java.util.ArrayList<>();
                        for (int x = 0; x < snapshot.getWidth(); x++) for (int y = 0; y < snapshot.getHeight(); y++) {
                            pixels.add(snapshot.getPixelReader().getArgb(x, y));
                            double opacity = snapshot.getPixelReader().getColor(x, y).getOpacity();
                            if (opacity > .1) foreground++;
                            if (opacity == 0) transparent++;
                        }
                        assertTrue(rendered.computeIfAbsent(size, ignored -> new HashSet<>()).add(pixels),
                                id + " has a duplicate rendered motif at size " + size);
                        assertTrue(foreground > 5, id + " size " + size + " has no painted foreground");
                        assertTrue(transparent > 5, id + " size " + size + " has no transparent background");
                        assertTrue(snapshot.getWidth() <= size + 2 && snapshot.getHeight() <= size + 2,
                                id + " exceeds icon cell");
                    }
                }
            } finally { stage.close(); }
        });
    }

    @Test void themeColorsAndRecentNumberBadgesChangeActualPixels() {
        FxTestUtil.runAndWait(() -> {
            StackPane root = new StackPane(); new Scene(root, 50, 50);
            Node icon = CommandIcons.create("menu.new", 24); root.getChildren().add(icon);
            for (String color : List.of("#eeeeee", "#111111", "#ffff00")) {
                root.setStyle("-fx-text-base-color: " + color + ";"); root.applyCss(); root.layout();
                SnapshotParameters params = new SnapshotParameters(); params.setFill(Color.TRANSPARENT);
                WritableImage image = icon.snapshot(params, null);
                boolean found = false;
                Color expected = Color.web(color);
                for (int x = 0; x < image.getWidth(); x++) for (int y = 0; y < image.getHeight(); y++) {
                    Color actual = image.getPixelReader().getColor(x, y);
                    if (actual.getOpacity() > .9 && Math.abs(actual.getRed() - expected.getRed()) < .05
                            && Math.abs(actual.getGreen() - expected.getGreen()) < .05) found = true;
                }
                assertTrue(found, "No foreground pixels in selected theme " + color);
            }
            assertNotEquals(CommandIcons.signature("menu.recentFiles.1"), CommandIcons.signature("menu.recentFiles.2"));
        });
    }

    @Test void rejectsUnmappedIdsAndInvalidSizesInsteadOfShowingDots() {
        assertFalse(CommandIcons.supports("unknown.missing"));
        assertThrows(IllegalArgumentException.class, () -> CommandIcons.create("unknown.missing", 18));
        assertThrows(NullPointerException.class, () -> CommandIcons.create(null, 18));
        for (double size : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, 257}) {
            assertThrows(IllegalArgumentException.class, () -> CommandIcons.create("menu.new", size));
        }
    }
}
