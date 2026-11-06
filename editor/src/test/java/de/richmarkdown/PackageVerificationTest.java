package de.richmarkdown;

import java.util.List;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackageVerificationTest {
    @org.junit.jupiter.api.BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @Test void capturesVisibleFontsAtThreeScalesInAShownWindow() throws Exception {
        FxTestUtil.runAndWait(() -> {
            Stage stage = new Stage();
            List<Text> samples = PackageVerification.samples();
            stage.setScene(new Scene(new VBox(samples.toArray(Text[]::new)), 800, 400));
            try {
                stage.show();
                stage.getScene().getRoot().applyCss();
                stage.getScene().getRoot().layout();
                var report = PackageVerification.capture(samples);
                assertEquals("7", report.getProperty("sampleCount"));
                for (int index = 0; index < samples.size(); index++) {
                    String prefix = "sample." + index + ".";
                    assertEquals(samples.get(index).getFont().getName(), report.getProperty(prefix + "font"));
                    int previous = 0;
                    for (String scale : List.of("1.0", "1.5", "2.0")) {
                        int ink = Integer.parseInt(report.getProperty(prefix + "ink." + scale));
                        assertTrue(ink > previous, "Visible glyph pixels must grow with scale");
                        previous = ink;
                    }
                }
            } finally { stage.hide(); }
        });
    }

    @Test void rejectsTinyAndInvisibleTextInsteadOfReportingSuccess() throws Exception {
        FxTestUtil.runAndWait(() -> {
            Text tiny = new Text("This text must not be accepted as readable");
            tiny.setFont(Font.font(4));
            assertThrows(IllegalStateException.class, () -> PackageVerification.capture(List.of(tiny)));
            Text invisible = new Text("This text must not be accepted as readable");
            invisible.setFill(Color.WHITE);
            assertThrows(IllegalStateException.class, () -> PackageVerification.capture(List.of(invisible)));
        });
    }
}
