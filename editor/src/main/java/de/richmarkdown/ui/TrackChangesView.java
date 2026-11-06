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

package de.richmarkdown.ui;

import com.gluonhq.richtextarea.model.Document;
import com.gluonhq.richtextarea.model.DecorationModel;
import com.gluonhq.richtextarea.model.ImageDecoration;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.markdown.Change;
import de.richmarkdown.markdown.ChangeStatus;
import de.richmarkdown.markdown.ChangeType;
import de.richmarkdown.markdown.MarkdownParser;
import de.richmarkdown.markdown.TrackChangesEngine;
import de.richmarkdown.markdown.ViewMode;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.layout.FlowPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Track Changes view - Word/LibreOffice Writer style inline diff with review pane.
 * Replaces the old side-by-side DiffView.
 */
public class TrackChangesView extends BorderPane {

    private static final Logger LOGGER = Logger.getLogger(TrackChangesView.class.getName());

    private final InlineDocumentView inlineView = new InlineDocumentView();
    private final ReviewPane reviewPane = new ReviewPane();
    private final SplitPane splitPane = new SplitPane();
    private final ComboBox<ViewMode> viewModeCombo = new ComboBox<>();

    private final ObjectProperty<ViewMode> viewModeProperty = new SimpleObjectProperty<>(this, "viewMode", ViewMode.ALL_MARKUP);

    private String leftContent = "";
    private String rightContent = "";
    private Path leftImageBaseDirectory;
    private Path rightImageBaseDirectory;
    private Document leftDocument = new Document();
    private Document rightDocument = new Document();
    private List<Change> currentChanges = List.of();
    private final Deque<ReviewState> undoHistory = new ArrayDeque<>();
    private final Deque<ReviewState> redoHistory = new ArrayDeque<>();
    private final ReadOnlyBooleanWrapper canUndo = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper canRedo = new ReadOnlyBooleanWrapper(false);
    private final ListView<Change> margin = new ListView<>();
    private List<Change> savedReview = List.of();

    private record ReviewState(List<Change> changes, String selectedId) { }

    /** Prepared off the FX thread; installed together to avoid intermediate comparisons. */
    public record Comparison(Document original, Document revised, List<Change> changes) {
        public Comparison {
            changes = List.copyOf(changes);
        }
    }

    public static Comparison prepare(String original, Path originalDirectory,
                                     String revised, Path revisedDirectory) {
        MarkdownParser parser = new MarkdownParser();
        if (originalDirectory == null) originalDirectory = Path.of(".").toAbsolutePath().normalize();
        if (revisedDirectory == null) revisedDirectory = Path.of(".").toAbsolutePath().normalize();
        Document left = parser.parseForComparison(original == null ? "" : original, originalDirectory);
        return prepare(left, originalDirectory, revised, revisedDirectory);
    }

    /** Compares an editor snapshot directly, avoiding a lossy Markdown round trip. */
    public static Comparison prepare(Document original, Path originalDirectory,
                                     String revised, Path revisedDirectory) {
        MarkdownParser parser = new MarkdownParser();
        if (originalDirectory == null) originalDirectory = Path.of(".").toAbsolutePath().normalize();
        if (revisedDirectory == null) revisedDirectory = Path.of(".").toAbsolutePath().normalize();
        Document left = parser.normalizeComparisonImages(original, originalDirectory);
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        Document right = parser.parseForComparison(revised == null ? "" : revised, revisedDirectory);
        return new Comparison(left, right, diffDocuments(left, right));
    }

    public void loadComparison(Comparison comparison) {
        leftDocument = comparison.original();
        rightDocument = comparison.revised();
        currentChanges = List.copyOf(comparison.changes());
        undoHistory.clear();
        redoHistory.clear();
        savedReview = currentChanges;
        refreshViews(null);
    }

    public void setSourceLabels(String original, String revised) {
        Label sources = new Label(java.text.MessageFormat.format(I18n.text("trackChanges.sources"), original, revised));
        sources.setWrapText(true);
        sources.setPadding(new Insets(8, 12, 0, 12));
        sources.setTooltip(new Tooltip(sources.getText()));
        setTop(sources);
    }

    public boolean hasReviewChanges() { return !savedReview.equals(currentChanges); }
    public void markReviewSaved() { savedReview = currentChanges; }
    /** Saving the result does not export review comments. */
    public void markResultSaved() {
        java.util.Map<String, String> exportedComments = new java.util.HashMap<>();
        for (Change saved : savedReview) exportedComments.put(saved.id(), saved.comment());
        savedReview = currentChanges.stream()
                .map(change -> change.withComment(exportedComments.get(change.id()))).toList();
    }

    public String getReviewReport(String original, String revised) {
        StringBuilder report = new StringBuilder("# ").append(I18n.text("trackChanges.report"))
                .append("\n\n").append(original).append(" → ").append(revised).append("\n");
        for (int i = 0; i < currentChanges.size(); i++) {
            Change change = currentChanges.get(i);
            report.append("\n## ").append(i + 1).append(". ").append(ReviewPane.typeText(change.type()))
                    .append(" · ").append(ReviewPane.statusText(change.status())).append("\n\n");
            String text = change.text().replace("\ufeff", I18n.text("trackChanges.image"));
            String fence = "```";
            while (text.contains(fence)) fence += "`";
            report.append(fence).append("text\n").append(text).append("\n").append(fence).append("\n");
            if (change.comment() != null) {
                report.append("\n").append(I18n.text("trackChanges.comment.label")).append("\n\n> ")
                        .append(change.comment().replace("\n", "\n> ")).append("\n");
            }
        }
        return report.toString();
    }
    public ReviewPane getReviewPane() { return reviewPane; }
    public Document getResultDocument() { return inlineView.getResultDocument(); }
    public String getResultMarkdown(Path directory) {
        return new de.richmarkdown.markdown.MarkdownWriter().write(getResultDocument(), directory);
    }

    /**
     * Creates the Track Changes view.
     */
    public TrackChangesView() {
        getStyleClass().add("rme-track-changes-view");
        setPrefSize(1000, 700);
        URL stylesheet = getClass().getResource("/de/richmarkdown/ui/app.css");
        if (stylesheet == null) {
            LOGGER.warning("Track changes stylesheet not found: /de/richmarkdown/ui/app.css");
        } else {
            getStylesheets().add(stylesheet.toExternalForm());
        }

        // Configure inline view
        inlineView.setOnChangeSelected(this::handleChangeSelected);
        inlineView.setMaxWidth(Double.MAX_VALUE);
        inlineView.setMaxHeight(Double.MAX_VALUE);

        // Configure review pane
        reviewPane.setOnAcceptChange(this::handleAcceptChange);
        reviewPane.setOnRejectChange(this::handleRejectChange);
        reviewPane.setOnSelectChange(this::handleSelectChange);
        reviewPane.setOnAcceptAll(this::handleAcceptAll);
        reviewPane.setOnRejectAll(this::handleRejectAll);
        reviewPane.setOnPrevious(this::handlePrevious);
        reviewPane.setOnNext(this::handleNext);
        reviewPane.setOnCommentChange(change -> updateChange(change, false));
        reviewPane.setOnResetChange(change -> decide(change, ChangeStatus.PENDING));

        // Configure split pane
        splitPane.setOrientation(Orientation.HORIZONTAL);
        margin.getStyleClass().add("rme-change-margin");
        margin.setPrefWidth(58);
        margin.setMinWidth(58);
        margin.setMaxWidth(58);
        margin.setFocusTraversable(false);
        margin.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(Change change, boolean empty) {
                super.updateItem(change, empty);
                setGraphic(null);
                setText(null);
                setTooltip(null);
                if (empty || change == null) return;
                javafx.scene.shape.Rectangle indicator = new javafx.scene.shape.Rectangle(5, 20,
                        javafx.scene.paint.Color.web(switch (change.type()) {
                            case INSERTED -> "#27ae60";
                            case DELETED -> "#e74c3c";
                            case FORMAT_CHANGED -> "#f39c12";
                        }));
                setGraphic(new HBox(3, indicator, new Label(String.valueOf(currentChanges.indexOf(change) + 1))));
                setTooltip(new Tooltip(ReviewPane.typeText(change.type()) + ": "
                        + change.text().replace("\ufeff", I18n.text("trackChanges.image"))));
            }
        });
        margin.getSelectionModel().selectedItemProperty().addListener((obs, before, change) -> {
            if (change != null) { reviewPane.selectChange(change); inlineView.selectChange(change); }
        });
        BorderPane documentPane = new BorderPane(inlineView);
        documentPane.setLeft(margin);
        splitPane.getItems().addAll(documentPane, reviewPane);
        splitPane.setDividerPositions(0.75); // 75% document, 25% review pane

        // Toolbar
        FlowPane toolbar = createToolbar();
        toolbar.getStyleClass().add("rme-track-changes-toolbar");

        // Layout
        VBox center = new VBox(6, toolbar, splitPane);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        setCenter(center);

        // Bind view mode
        viewModeProperty.bindBidirectional(inlineView.viewModeProperty());
        viewModeCombo.valueProperty().bindBidirectional(viewModeProperty);
        viewModeProperty.addListener((obs, before, after) -> updateMargin());
        updateMargin();
        addEventFilter(KeyEvent.KEY_PRESSED, this::handleReviewKey);
    }

    void handleReviewKey(KeyEvent event) {
            if (event.isShortcutDown() && event.getCode() == KeyCode.Z) {
                if (event.isShiftDown()) redo(); else undo();
                event.consume();
            } else if (event.isShortcutDown() && event.getCode() == KeyCode.Y) {
                redo(); event.consume();
            } else if (event.isShortcutDown() && event.getCode() == KeyCode.ENTER) {
                Change selected = reviewPane.getSelectedChange();
                if (selected != null && selected.status() == ChangeStatus.PENDING) decide(selected, ChangeStatus.ACCEPTED);
                event.consume();
            } else if (event.isShortcutDown() && event.getCode() == KeyCode.BACK_SPACE) {
                Change selected = reviewPane.getSelectedChange();
                if (selected != null && (event.isShiftDown() || selected.status() == ChangeStatus.PENDING)) {
                    decide(selected, event.isShiftDown() ? ChangeStatus.PENDING : ChangeStatus.REJECTED);
                }
                event.consume();
            } else if (event.getCode() == KeyCode.F7) {
                if (event.isShiftDown()) handlePrevious(); else handleNext();
                event.consume();
            }
    }

    private FlowPane createToolbar() {
        Label titleLabel = new Label(I18n.text("trackChanges.title"));
        titleLabel.getStyleClass().add("rme-track-changes-title");

        // View mode selector
        viewModeCombo.setItems(FXCollections.observableArrayList(ViewMode.values()));
        viewModeCombo.setValue(ViewMode.ALL_MARKUP);
        viewModeCombo.setCellFactory(cb -> new ViewModeCell());
        viewModeCombo.setButtonCell(new ViewModeCell());
        viewModeCombo.setTooltip(new Tooltip(I18n.text("trackChanges.viewMode.tooltip")));
        viewModeCombo.getStyleClass().add("rme-view-mode-combo");

        Button undo = ReviewIcons.button("undo", this::undo);
        Button redo = ReviewIcons.button("redo", this::redo);
        undo.disableProperty().bind(canUndo.not());
        redo.disableProperty().bind(canRedo.not());
        FlowPane toolbar = new FlowPane(12, 6, titleLabel, viewModeCombo, undo, redo);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8, 12, 8, 12));
        return toolbar;
    }

    /**
     * Loads the left (original) document content.
     */
    public void loadLeft(String markdown) {
        loadLeft(markdown, null);
    }

    /**
     * Loads the original Markdown and resolves its local images relative to its file.
     */
    public void loadLeft(String markdown, Path imageBaseDirectory) {
        this.leftContent = markdown == null ? "" : markdown;
        this.leftImageBaseDirectory = imageBaseDirectory;
        recompute();
    }

    /**
     * Loads the right (revised) document content.
     */
    public void loadRight(String markdown) {
        loadRight(markdown, null);
    }

    /**
     * Loads the revised Markdown and resolves its local images relative to its file.
     */
    public void loadRight(String markdown, Path imageBaseDirectory) {
        this.rightContent = markdown == null ? "" : markdown;
        this.rightImageBaseDirectory = imageBaseDirectory;
        recompute();
    }

    /**
     * Recomputes the diff and updates the view.
     */
    private void recompute() {
        loadComparison(prepare(leftContent, leftImageBaseDirectory, rightContent, rightImageBaseDirectory));
    }

    private static List<Change> diffDocuments(Document oldDocument, Document newDocument) {
        String oldText = oldDocument.getText();
        String newText = newDocument.getText();
        List<Change> textChanges = TrackChangesEngine.diff(oldText, newText);
        List<Change> allChanges = new ArrayList<>(textChanges);
        int oldIndex = 0;
        int newIndex = 0;

        for (Change change : textChanges) {
            int equalLength = Math.max(0, Math.min(oldText.length(), change.startOffset()) - oldIndex);
            addFormattingChanges(oldDocument, newDocument, oldText, oldIndex, newIndex,
                    equalLength, allChanges);
            oldIndex += equalLength;
            newIndex += equalLength;

            if (change.type() == ChangeType.INSERTED) {
                newIndex += change.text().length();
            } else if (change.type() == ChangeType.DELETED) {
                oldIndex += change.endOffset() - change.startOffset();
            }
        }

        int trailingLength = Math.min(oldText.length() - oldIndex, newText.length() - newIndex);
        addFormattingChanges(oldDocument, newDocument, oldText, oldIndex, newIndex,
                trailingLength, allChanges);
        allChanges.sort((first, second) -> Integer.compare(first.startOffset(), second.startOffset()));
        return List.copyOf(allChanges);
    }

    private static void addFormattingChanges(Document oldDocument, Document newDocument, String oldText,
                                      int oldStart, int newStart, int length, List<Change> changes) {
        int offset = 0;
        int changeStart = -1;
        while (offset < length) {
            int oldOffset = oldStart + offset;
            int newOffset = newStart + offset;
            DecorationModel oldStyle = decorationAt(oldDocument, oldOffset);
            DecorationModel newStyle = decorationAt(newDocument, newOffset);
            boolean changed = !sameStyle(oldStyle, newStyle);
            int oldRunLength = nextStyleBoundary(oldDocument, oldOffset, oldStyle) - oldOffset;
            int newRunLength = nextStyleBoundary(newDocument, newOffset, newStyle) - newOffset;
            int nextOffset = offset + Math.min(length - offset,
                    Math.max(1, Math.min(oldRunLength, newRunLength)));

            if (changed && changeStart < 0) {
                changeStart = offset;
            } else if (!changed && changeStart >= 0) {
                addFormattingChange(oldText, oldStart, changeStart, offset, changes);
                changeStart = -1;
            }
            offset = nextOffset;
        }
        if (changeStart >= 0) {
            addFormattingChange(oldText, oldStart, changeStart, length, changes);
        }
    }

    private static DecorationModel decorationAt(Document document, int position) {
        List<DecorationModel> decorations = document.getDecorations();
        int low = 0;
        int high = decorations.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            DecorationModel decoration = decorations.get(middle);
            int end = decoration.getStart() + decoration.getLength();
            if (position < decoration.getStart()) {
                high = middle - 1;
            } else if (position >= end) {
                low = middle + 1;
            } else {
                return decoration;
            }
        }
        return null;
    }

    private static int nextStyleBoundary(Document document, int position, DecorationModel current) {
        if (current != null) {
            return current.getStart() + current.getLength();
        }
        int low = 0;
        int high = document.getDecorations().size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (document.getDecorations().get(middle).getStart() <= position) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low < document.getDecorations().size()
                ? document.getDecorations().get(low).getStart()
                : Integer.MAX_VALUE;
    }

    private static boolean sameStyle(DecorationModel first, DecorationModel second) {
        return sameDecoration(first == null ? null : first.getDecoration(),
                second == null ? null : second.getDecoration())
                && Objects.equals(first == null ? null : first.getParagraphDecoration(),
                        second == null ? null : second.getParagraphDecoration());
    }

    private static boolean sameDecoration(Object first, Object second) {
        if (first instanceof ImageDecoration firstImage) {
            if (!(second instanceof ImageDecoration secondImage)) {
                return false;
            }
            return Objects.equals(firstImage.getUrl(), secondImage.getUrl())
                    && firstImage.getWidth() == secondImage.getWidth()
                    && firstImage.getHeight() == secondImage.getHeight()
                    && Objects.equals(firstImage.getLink(), secondImage.getLink());
        }
        return !(second instanceof ImageDecoration) && Objects.equals(first, second);
    }

    private static void addFormattingChange(String oldText, int oldStart, int start, int end,
                                            List<Change> changes) {
        int startOffset = oldStart + start;
        int endOffset = oldStart + end;
        changes.add(new Change(null, ChangeType.FORMAT_CHANGED, startOffset, endOffset,
                oldText.substring(startOffset, endOffset), "Author", Instant.now(), null, ChangeStatus.PENDING));
    }

    /**
     * Handles change selection from inline view.
     */
    private void handleChangeSelected(InlineDocumentView.ChangeSelectionEvent event) {
        reviewPane.selectChange(event.getChange());
        inlineView.selectChange(event.getChange());
    }

    /**
     * Handles change selection from review pane.
     */
    private void handleSelectChange(Change change) {
        inlineView.selectChange(change);
    }

    /**
     * Accepts a single change.
     */
    private void handleAcceptChange(Change change) {
        decide(change, ChangeStatus.ACCEPTED);
    }

    private void handleRejectChange(Change change) { decide(change, ChangeStatus.REJECTED); }

    /** Decisions always use the current version, preserving comments and identity. */
    public void decide(Change change, ChangeStatus status) {
        currentChanges.stream().filter(item -> item.id().equals(change.id())).findFirst()
                .ifPresent(current -> updateChange(current.withStatus(status), status != ChangeStatus.PENDING));
    }

    public void setComment(Change change, String comment) {
        currentChanges.stream().filter(item -> item.id().equals(change.id())).findFirst()
                .ifPresent(current -> updateChange(current.withComment(comment == null || comment.isBlank() ? null : comment), false));
    }

    private ReviewState snapshot() {
        Change selected = reviewPane.getSelectedChange();
        return new ReviewState(currentChanges, selected == null ? null : selected.id());
    }

    private void remember() {
        undoHistory.push(snapshot());
        if (undoHistory.size() > 100) undoHistory.removeLast();
        redoHistory.clear();
    }

    private void updateChange(Change updated, boolean advance) {
        for (int i = 0; i < currentChanges.size(); i++) {
            if (!currentChanges.get(i).id().equals(updated.id())) continue;
            if (currentChanges.get(i).equals(updated)) return;
            remember();
            List<Change> replacement = new ArrayList<>(currentChanges);
            replacement.set(i, updated);
            currentChanges = List.copyOf(replacement);
            String selected = updated.id();
            if (advance) {
                // Continue forward, then wrap to any remaining pending item.
                for (int step = 1; step <= currentChanges.size(); step++) {
                    Change candidate = currentChanges.get((i + step) % currentChanges.size());
                    if (candidate.status() == ChangeStatus.PENDING) { selected = candidate.id(); break; }
                }
            }
            refreshViews(selected);
            return;
        }
    }

    public void acceptAll() { decideAll(ChangeStatus.ACCEPTED); }
    public void rejectAll() { decideAll(ChangeStatus.REJECTED); }
    private void handleAcceptAll() { acceptAll(); }
    private void handleRejectAll() { rejectAll(); }

    private void decideAll(ChangeStatus status) {
        if (currentChanges.stream().noneMatch(change -> change.status() == ChangeStatus.PENDING)) return;
        ReviewState before = snapshot();
        remember();
        currentChanges = currentChanges.stream()
                .map(change -> change.status() == ChangeStatus.PENDING ? change.withStatus(status) : change).toList();
        refreshViews(before.selectedId());
    }

    public boolean canUndo() { return canUndo.get(); }
    public boolean canRedo() { return canRedo.get(); }
    public void undo() { restore(undoHistory, redoHistory); }
    public void redo() { restore(redoHistory, undoHistory); }

    private void restore(Deque<ReviewState> source, Deque<ReviewState> destination) {
        if (source.isEmpty()) return;
        destination.push(snapshot());
        ReviewState previous = source.pop();
        currentChanges = previous.changes();
        refreshViews(previous.selectedId());
    }

    private void refreshViews(String selectedId) {
        inlineView.setDocuments(leftDocument, rightDocument, currentChanges);
        reviewPane.setChanges(currentChanges);
        if (selectedId != null) currentChanges.stream().filter(change -> change.id().equals(selectedId))
                .findFirst().ifPresent(reviewPane::selectChange);
        canUndo.set(!undoHistory.isEmpty());
        canRedo.set(!redoHistory.isEmpty());
        updateMargin();
    }

    private void updateMargin() {
        boolean visible = getViewMode() == ViewMode.SIMPLE_MARKUP || getViewMode() == ViewMode.ALL_MARKUP;
        margin.setVisible(visible);
        margin.setManaged(visible);
        margin.getItems().setAll(currentChanges.stream().filter(change -> change.status() == ChangeStatus.PENDING).toList());
    }

    private void handlePrevious() { reviewPane.selectPrevious(); }
    private void handleNext() { reviewPane.selectNext(); }

    /**
     * Gets the current view mode.
     */
    public ViewMode getViewMode() {
        return viewModeProperty.get();
    }

    /**
     * Sets the view mode.
     */
    public void setViewMode(ViewMode mode) {
        viewModeProperty.set(mode);
    }

    public ObjectProperty<ViewMode> viewModeProperty() {
        return viewModeProperty;
    }

    /**
     * Gets the current changes.
     */
    public List<Change> getChanges() {
        return List.copyOf(currentChanges);
    }

    /**
     * Gets the inline document view (for testing).
     */
    public InlineDocumentView getInlineView() {
        return inlineView;
    }

    /**
     * Custom cell for ViewMode combo box.
     */
    private static class ViewModeCell extends javafx.scene.control.ListCell<ViewMode> {
        @Override
        protected void updateItem(ViewMode mode, boolean empty) {
            super.updateItem(mode, empty);
            if (empty || mode == null) {
                setText(null);
            } else {
                setText(switch (mode) {
                    case ALL_MARKUP -> I18n.text("trackChanges.viewMode.allMarkup");
                    case SIMPLE_MARKUP -> I18n.text("trackChanges.viewMode.simpleMarkup");
                    case NO_MARKUP -> I18n.text("trackChanges.viewMode.noMarkup");
                    case ORIGINAL -> I18n.text("trackChanges.viewMode.original");
                });
            }
        }
    }
}
