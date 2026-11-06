package de.richmarkdown.ui;

import de.richmarkdown.features.printing.PrintOptions;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;

/** A clipped body plus print-only header/footer, reused for all pages in one job. */
public final class PrintPageView extends Pane {
    private final Pane body;
    private final Label header = new Label();
    private final Label footer = new Label();
    private final Node content;
    private final PrintOptions options;
    private final double contentHeight;
    public PrintPageView(Node content, double width, double height, PrintOptions options) {
        this.content = content; this.options = options;
        contentHeight = bodyHeight(height, options);
        double top = options.header().isEmpty() ? 0 : 24;
        body = new Pane(content); body.setClip(new Rectangle(width, contentHeight));
        body.relocate(0, top); body.setPrefSize(width, contentHeight);
        header.setText(options.header()); header.relocate(0, 0);
        footer.relocate(0, height - 22);
        header.setStyle("-fx-text-fill: black; -fx-font-size: 10px;"); footer.setStyle(header.getStyle());
        getChildren().addAll(body, header, footer);
        setPrefSize(width, height); resize(width, height);
    }
    public static double bodyHeight(double height, PrintOptions options) {
        double result = height - (options.header().isEmpty() ? 0 : 24)
                - (options.footer().isEmpty() && !options.pageNumbers() ? 0 : 24);
        if (!Double.isFinite(result) || result < 40) throw new IllegalArgumentException("Printable page body is too small");
        return result;
    }
    public void showPage(int page, int total) {
        if (page < 1 || total < page) throw new IllegalArgumentException("Invalid page");
        content.setTranslateY(-(page - 1) * contentHeight);
        footer.setText(options.footer() + (options.pageNumbers() ? (options.footer().isEmpty() ? "" : " · ") + page + " / " + total : ""));
    }
    public String footerText() { return footer.getText(); }
    public double contentHeight() { return contentHeight; }
}
