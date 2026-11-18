package de.richmarkdown.ui;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.model.Document;
import com.gluonhq.richtextarea.viewmodel.ActionCmdFactory;
import de.richmarkdown.features.printing.PrintOptions;
import de.richmarkdown.ui.dialogs.*;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Uses the same snapshot, text measurement, body clipping and page furniture as printing. */
public final class PrintPreview {
    private PrintPreview() { }
    public static void show(Window owner, de.richmarkdown.editor.MarkdownEditor editor, Document document, PrintDialogResult choice) {
        PrintOptions options = choice.options();
        var layout = choice.printer().createPageLayout(choice.paper(), choice.orientation(),
            PrintOptions.points(options.left()), PrintOptions.points(options.right()),
            PrintOptions.points(options.top()), PrintOptions.points(options.bottom()));
        Stage stage = new Stage(); stage.initOwner(owner); stage.setTitle(WorkflowDialogs.text("preview"));
        var copy = editor.createPrintCopy(); copy.setPrefWidth(layout.getPrintableWidth());
        Pane host = new Pane(copy); host.setPrefSize(layout.getPrintableWidth(), layout.getPrintableHeight());
        ScrollPane scroll = new ScrollPane(host); Label status = new Label();
        ComboBox<Integer> page = new ComboBox<>(); page.setDisable(true);
        Slider zoom = new Slider(.25, 1.5, .7); zoom.setAccessibleText(WorkflowDialogs.text("preview"));
        VBox body = new VBox(8, new HBox(10, new Label(WorkflowDialogs.text("preview")), page, zoom), scroll, status);
        VBox.setVgrow(scroll, Priority.ALWAYS); DialogButtons.applyPanelStyles(body, null);
        stage.setScene(new Scene(body, 850, 780)); stage.show(); body.applyCss();
        if (copy.getSkin() instanceof RichTextAreaSkin skin) new ActionCmdFactory().open(document).apply(skin.getViewModel());
        Platform.runLater(() -> Platform.runLater(() -> {
            if (!stage.isShowing()) return;
            try {
                copy.resize(layout.getPrintableWidth(), layout.getPrintableHeight());
                double height = PrintSupport.measureContentHeight(copy);
                copy.setPrefHeight(height); copy.resize(layout.getPrintableWidth(), height); copy.applyCss(); copy.layout();
                int total = PrintSupport.pageCount(height, PrintPageView.bodyHeight(layout.getPrintableHeight(), options));
                var selected = options.pages(total); page.getItems().setAll(selected);
                host.getChildren().remove(copy);
                PrintPageView view = new PrintPageView(copy, layout.getPrintableWidth(), layout.getPrintableHeight(), options);
                Pane paper = new Pane(view); paper.setStyle("-fx-background-color: white;");
                double paperWidth = layout.getPrintableWidth() + layout.getLeftMargin() + layout.getRightMargin();
                double paperHeight = layout.getPrintableHeight() + layout.getTopMargin() + layout.getBottomMargin();
                paper.setMinSize(paperWidth, paperHeight); paper.setPrefSize(paperWidth, paperHeight); paper.setMaxSize(paperWidth, paperHeight);
                view.relocate(layout.getLeftMargin(), layout.getTopMargin());
                javafx.scene.transform.Scale scale = new javafx.scene.transform.Scale(zoom.getValue(), zoom.getValue(), 0, 0);
                scale.xProperty().bind(zoom.valueProperty()); scale.yProperty().bind(zoom.valueProperty()); paper.getTransforms().add(scale);
                scroll.setContent(new Group(paper));
                page.valueProperty().addListener((obs, old, value) -> { if (value != null) view.showPage(value, total); });
                page.setDisable(false); page.getSelectionModel().selectFirst();
                status.setText(WorkflowDialogs.text("selected") + ": " + selected.size() + " / " + total);
            } catch (RuntimeException failure) { status.setText(failure.getMessage()); }
        }));
    }
}
