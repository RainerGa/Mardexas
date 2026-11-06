package de.richmarkdown;

import de.richmarkdown.i18n.I18n;
import de.richmarkdown.io.WorkspaceSession;
import de.richmarkdown.settings.ApplicationSettings;
import de.richmarkdown.ui.UiMode;
import de.richmarkdown.ui.WorkspaceWindow;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import javafx.util.Duration;

/** Opt-in release check, run in a separate process on the target OS. */
public final class PackageVerification extends Application {
    private static volatile Throwable failure;

    static void run(String reportDirectory) {
        failure = null;
        launch(PackageVerification.class, reportDirectory);
        if (failure != null) {
            throw new IllegalStateException("Packaged application verification failed", failure);
        }
    }

    @Override
    public void start(Stage unused) {
        WorkspaceWindow workspace = null;
        try {
            Path output = Path.of(getParameters().getRaw().getFirst()).toAbsolutePath();
            Files.createDirectories(output);
            System.setProperty("rme.config.dir", output.resolve("profile").toString());
            System.setProperty("java.util.prefs.userRoot", output.resolve("preferences").toString());
            I18n.setSettingsStore(ApplicationSettings.inMemory());
            // Load every supported resource bundle while the native-image agent
            // is active; use English for the comparable screenshots.
            for (String language : I18n.supportedLanguages()) {
                I18n.setCurrentLanguage(language);
                I18n.text("menu.file");
            }
            I18n.setCurrentLanguage("en");
            workspace = new WorkspaceWindow(new WorkspaceSession(output.resolve("workspace.properties")));
            var editor = workspace.newDocument();
            editor.getCommandSurface().setMode(UiMode.RIBBON);
            editor.getEditor().setMarkdown("# Package verification\n\nReadable **bold**, *italic* and `code`: ÄÖÜ äöü ß.\n\n- List item\n\n| A | B |\n| --- | --- |\n| One | Two |\n");
            List<Text> samples = samples();
            VBox samplePane = new VBox(4);
            samplePane.setStyle("-fx-background-color: white; -fx-padding: 8;");
            samplePane.getChildren().add(new Label("System font / Systemschrift"));
            samplePane.getChildren().addAll(samples);
            ((BorderPane) workspace.getScene().getRoot()).setBottom(samplePane);
            workspace.show();
            WorkspaceWindow shown = workspace;
            PauseTransition render = new PauseTransition(Duration.seconds(1));
            render.setOnFinished(event -> {
                try {
                    shown.getScene().getRoot().applyCss();
                    shown.getScene().getRoot().layout();
                    Properties report = capture(samples);
                    report.setProperty("os", System.getProperty("os.name"));
                    report.setProperty("architecture", System.getProperty("os.arch"));
                    report.setProperty("outputScaleX", Double.toString(shown.getOutputScaleX()));
                    report.setProperty("outputScaleY", Double.toString(shown.getOutputScaleY()));
                    report.setProperty("editorTextLength", Integer.toString(editor.getEditor().getDocumentSnapshot().getText().length()));
                    try (Writer writer = Files.newBufferedWriter(output.resolve("fonts.properties"), StandardCharsets.UTF_8)) {
                        report.store(writer, "Mardexas package rendering check");
                    }
                    writePpm(shown.getScene().snapshot(null), output.resolve("window.ppm"));
                    System.out.println("PACKAGE_VERIFICATION_OK " + output);
                } catch (Throwable problem) {
                    failure = problem;
                    problem.printStackTrace(System.err);
                } finally {
                    shown.dispose();
                    shown.hide();
                    Platform.exit();
                }
            });
            render.play();
        } catch (Throwable problem) {
            failure = problem;
            problem.printStackTrace(System.err);
            if (workspace != null) {
                workspace.dispose();
                workspace.hide();
            }
            Platform.exit();
        }
    }

    static List<Text> samples() {
        List<Text> samples = new ArrayList<>();
        samples.add(new Text("System: Mardexas ÄÖÜ äöü ß 0123456789"));
        for (FontWeight weight : List.of(FontWeight.NORMAL, FontWeight.BOLD)) {
            for (FontPosture posture : FontPosture.values()) {
                Text text = new Text("Readable Markdown: MWiIl1 ÄÖÜ äöü ß 0123456789");
                text.setFont(Font.font("System", weight, posture, 14));
                samples.add(text);
            }
        }
        Text code = new Text("Monospaced: iiii WWWW 0123456789 {} []");
        code.setFont(Font.font("Monospaced", 14));
        samples.add(code);
        Text heading = new Text("Heading: Mardexas ÄÖÜ");
        heading.setFont(Font.font("System", FontWeight.BOLD, 24));
        samples.add(heading);
        return List.copyOf(samples);
    }

    static Properties capture(List<Text> samples) {
        Properties report = new Properties();
        report.setProperty("sampleCount", Integer.toString(samples.size()));
        for (int index = 0; index < samples.size(); index++) {
            Text text = samples.get(index);
            String prefix = "sample." + index + ".";
            if (text.getFont().getSize() < 9 || text.getLayoutBounds().getWidth() < 60
                    || text.getLayoutBounds().getHeight() < 9) {
                throw new IllegalStateException("Invalid font metrics: " + text.getFont());
            }
            report.setProperty(prefix + "font", text.getFont().getName());
            report.setProperty(prefix + "size", Double.toString(text.getFont().getSize()));
            report.setProperty(prefix + "width", Double.toString(text.getLayoutBounds().getWidth()));
            report.setProperty(prefix + "height", Double.toString(text.getLayoutBounds().getHeight()));
            for (double scale : new double[] {1, 1.5, 2}) {
                SnapshotParameters parameters = new SnapshotParameters();
                parameters.setFill(Color.WHITE);
                parameters.setTransform(new Scale(scale, scale));
                Image image = text.snapshot(parameters, null);
                int ink = 0;
                for (int y = 0; y < (int) image.getHeight(); y++) {
                    for (int x = 0; x < (int) image.getWidth(); x++) {
                        Color pixel = image.getPixelReader().getColor(x, y);
                        if (pixel.getOpacity() > .5 && pixel.getBrightness() < .65) ink++;
                    }
                }
                if (ink < 50 * scale * scale) {
                    throw new IllegalStateException("Text rendered with insufficient visible pixels: " + index);
                }
                report.setProperty(prefix + "ink." + scale, Integer.toString(ink));
            }
        }
        return report;
    }

    private static void writePpm(Image image, Path destination) throws IOException {
        try (OutputStream stream = Files.newOutputStream(destination)) {
            int width = (int) image.getWidth();
            int height = (int) image.getHeight();
            stream.write(("P6\n" + width + " " + height + "\n255\n").getBytes(StandardCharsets.US_ASCII));
            byte[] row = new byte[width * 3];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getPixelReader().getArgb(x, y);
                    row[x * 3] = (byte) (argb >> 16);
                    row[x * 3 + 1] = (byte) (argb >> 8);
                    row[x * 3 + 2] = (byte) argb;
                }
                stream.write(row);
            }
        }
    }
}
