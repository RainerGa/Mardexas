/*
 * Copyright (C) 2026 Rainer Garbotz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package de.richmarkdown.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/** Local MIT-licensed Tabler vectors with explicit semantic command mappings. */
public final class CommandIcons {
    private static final String ROOT = "/de/richmarkdown/ui/icons/tabler/";
    private static final Map<String, String> COMMANDS = properties("commands.properties");
    private static final Map<String, List<String>> PATHS = geometry();
    private static final String[] DIGITS = {
        "M1 0H5V8H1ZM1 0L5 8", "M2 2L4 0V8M2 8H6", "M1 0H5V4H1V8H5",
        "M1 0H5V8H1M2 4H5", "M1 0V4H5M5 0V8", "M5 0H1V4H5V8H1",
        "M5 0H1V8H5V4H1", "M1 0H5L2 8", "M1 0H5V8H1ZM1 4H5",
        "M5 8V0H1V4H5"
    };

    private CommandIcons() { }

    /** Creates independent scene-graph nodes; immutable path data is shared. */
    public static Node create(String commandId, double size) {
        if (!Double.isFinite(size) || size <= 0 || size > 256) {
            throw new IllegalArgumentException("Icon size must be in (0, 256]");
        }
        String motif = motif(commandId);
        Pane result = new Pane();
        result.getStyleClass().addAll("rme-command-icon", "rme-icon");
        result.setMinSize(size, size);
        result.setPrefSize(size, size);
        result.setMaxSize(size, size);
        result.setMouseTransparent(true);
        result.getProperties().put("commandId", commandId);
        Group canvas = new Group();
        canvas.setManaged(false);
        canvas.getTransforms().add(new Scale(size / 24, size / 24, 0, 0));
        String[] sources = motif.split("\\+");
        for (int index = 0; index < sources.length; index++) {
            Group part = shape(sources[index]);
            if (sources.length > 1 || recentNumber(commandId) != null) {
                part.getTransforms().add(new Scale(index == 0 ? .72 : .44, index == 0 ? .72 : .44, 0, 0));
                if (index > 0) { part.setTranslateX(13); part.setTranslateY(13); }
            }
            canvas.getChildren().add(part);
        }
        String number = recentNumber(commandId);
        if (number != null) {
            Group badge = new Group();
            for (int i = 0; i < number.length(); i++) {
                SVGPath digit = path("S|" + DIGITS[number.charAt(i) - '0']);
                digit.setStrokeWidth(1.5);
                digit.setTranslateX(i * 7);
                badge.getChildren().add(digit);
            }
            double scale = Math.min(1.0, 10.0 / (number.length() * 7));
            badge.getTransforms().add(new Scale(scale, scale, 0, 0));
            badge.setTranslateX(14); badge.setTranslateY(15);
            canvas.getChildren().add(badge);
        }
        result.getChildren().add(canvas);
        return result;
    }

    /** Canonical rendered geometry, including semantic variant badges. */
    public static String signature(String commandId) {
        StringBuilder signature = new StringBuilder();
        for (String source : motif(commandId).split("\\+")) {
            signature.append('[');
            for (String path : PATHS.get(source)) signature.append(path);
            signature.append(']');
        }
        String number = recentNumber(commandId);
        if (number != null) signature.append("number:").append(number);
        return signature.toString();
    }

    public static Set<String> supportedCommandIds() { return COMMANDS.keySet(); }
    public static String sourceRelease() { return "Tabler Icons v3.34.1"; }
    public static String licenseResource() { return ROOT + "LICENSE"; }
    public static List<String> sourceNames(String commandId) {
        return List.of(motif(commandId).split("\\+"));
    }
    public static boolean supports(String commandId) {
        return commandId != null && (COMMANDS.containsKey(commandId) || recentNumber(commandId) != null);
    }

    private static String motif(String id) {
        Objects.requireNonNull(id, "commandId");
        String result = COMMANDS.get(id);
        if (result != null) return result;
        if (recentNumber(id) != null) return "file-time";
        throw new IllegalArgumentException("Missing explicit command icon: " + id);
    }

    private static String recentNumber(String id) {
        if (id == null) return null;
        for (String prefix : List.of("file.recent.", "menu.recentFiles.entry.", "recent.file.", "menu.recentFiles.")) {
            if (id.startsWith(prefix)) {
                String suffix = id.substring(prefix.length());
                if (!suffix.isEmpty() && suffix.length() <= 4 && suffix.chars().allMatch(Character::isDigit)) return suffix;
            }
        }
        return null;
    }

    private static Group shape(String source) {
        List<SVGPath> paths = new ArrayList<>();
        for (String data : PATHS.getOrDefault(source, List.of())) paths.add(path(data));
        if (paths.isEmpty()) throw new IllegalStateException("Missing vector geometry: " + source);
        return new Group(paths.toArray(Node[]::new));
    }

    private static SVGPath path(String data) {
        SVGPath path = new SVGPath();
        path.setContent(data.substring(2));
        boolean filled = data.startsWith("F|");
        path.setFill(filled ? Color.BLACK : Color.TRANSPARENT);
        path.setStroke(filled ? null : Color.BLACK);
        path.setStrokeWidth(1.8);
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.setStrokeLineJoin(StrokeLineJoin.ROUND);
        path.getStyleClass().addAll("rme-command-icon-shape", "rme-icon-shape");
        path.setStyle(filled ? "-fx-fill: -fx-text-base-color; -fx-stroke: transparent;"
                : "-fx-fill: transparent; -fx-stroke: -fx-text-base-color;");
        return path;
    }

    private static Map<String, List<String>> geometry() {
        Map<String, List<String>> grouped = new TreeMap<>();
        properties("paths.properties").entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String source = entry.getKey().substring(0, entry.getKey().lastIndexOf('.'));
            grouped.computeIfAbsent(source, ignored -> new ArrayList<>()).add(entry.getValue());
        });
        Map<String, List<String>> immutable = new TreeMap<>();
        grouped.forEach((source, paths) -> immutable.put(source, List.copyOf(paths)));
        return Map.copyOf(immutable);
    }

    private static Map<String, String> properties(String name) {
        Properties values = new Properties();
        try (InputStream input = CommandIcons.class.getResourceAsStream(ROOT + name)) {
            if (input == null) throw new IllegalStateException("Missing icon resource: " + name);
            values.load(input);
        } catch (IOException failure) { throw new ExceptionInInitializerError(failure); }
        Map<String, String> result = new TreeMap<>();
        values.forEach((key, value) -> result.put(key.toString(), value.toString()));
        return Map.copyOf(result);
    }
}
