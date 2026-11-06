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

import com.gluonhq.richtextarea.RichTextArea;
import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.model.DecorationModel;
import com.gluonhq.richtextarea.model.Document;
import com.gluonhq.richtextarea.model.ParagraphDecoration;
import com.gluonhq.richtextarea.model.Decoration;
import com.gluonhq.richtextarea.model.ImageDecoration;
import com.gluonhq.richtextarea.model.TextDecoration;
import com.gluonhq.richtextarea.viewmodel.RichTextAreaViewModel;
import de.richmarkdown.markdown.Change;
import de.richmarkdown.markdown.ChangeStatus;
import de.richmarkdown.markdown.ChangeType;
import de.richmarkdown.markdown.ChangeViewMode;
import de.richmarkdown.markdown.TrackChangesEngine;
import de.richmarkdown.markdown.ViewMode;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventType;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Inline document view for Track Changes.
 * Displays a single document with changes rendered inline using RichTextArea decorations.
 */
public class InlineDocumentView extends RichTextArea {

    private final ObjectProperty<ViewMode> viewModeProperty = new SimpleObjectProperty<>(this, "viewMode", ViewMode.ALL_MARKUP);
    private final ObjectProperty<javafx.event.EventHandler<ChangeSelectionEvent>> onChangeSelectedProperty = new SimpleObjectProperty<>();

    @Override
    public String getUserAgentStylesheet() {
        URL defaultStylesheetUrl = RichTextArea.class.getResource("rich-text-area.css");
        return defaultStylesheetUrl != null ? defaultStylesheetUrl.toExternalForm() : null;
    }

    private List<Change> changes = List.of();
    private String oldText = "";
    private String newText = "";
    private Document oldDocument = new Document();
    private Document newDocument = new Document();
    private String renderedText = "";
    private List<RenderedChange> renderedChanges = List.of();
    private String pendingSelectionId;
    private String selectedChangeId;

    private record RenderedChange(Change change, int start, int end) {
    }

    /**
     * Creates the inline document view.
     */
    public InlineDocumentView() {
        getStyleClass().add("rme-inline-doc");
        setEditable(false);
        setPrefWidth(600);
        setPrefHeight(400);
        documentProperty().addListener((observable, oldDocument, newDocument) -> {
            if (newDocument != null && pendingSelectionId != null) {
                Platform.runLater(this::applyPendingSelection);
            }
        });

        // Listen for click on changes
        setOnMouseClicked(event -> {
            if (event.getClickCount() == 1) {
                int position = getCaretPosition();
                if (getSkin() instanceof RichTextAreaSkin skin && skin.getViewModel() != null) {
                    position = skin.getViewModel().getTextBuffer().getText(0, position).length();
                }
                Change change = findChangeAtPosition(position);
                if (change == null && position > 0) {
                    change = findChangeAtPosition(position - 1);
                }
                if (change != null) {
                    fireChangeSelected(change);
                }
            }
        });

        viewModeProperty.addListener((obs, old, newVal) -> rebuildDocument());
    }

    /**
     * Sets the changes to display and rebuilds the document.
     *
     * @param changes  the changes to display
     * @param oldText  the original text (base document)
     */
    public void setChanges(List<Change> changes, String oldText) {
        String sourceText = oldText == null ? "" : oldText;
        List<Change> safeChanges = changes == null ? List.of() : List.copyOf(changes);
        setDocuments(new Document(sourceText),
                new Document(TrackChangesEngine.applyChanges(sourceText, safeChanges)), safeChanges);
    }

    /**
     * Sets parsed source and revised documents, preserving their Markdown styling.
     */
    public void setDocuments(Document oldDocument, Document newDocument, List<Change> changes) {
        this.changes = changes == null ? List.of() : List.copyOf(changes);
        this.oldDocument = oldDocument == null ? new Document() : oldDocument;
        this.newDocument = newDocument == null ? new Document() : newDocument;
        this.oldText = this.oldDocument.getText();
        this.newText = this.newDocument.getText();
        rebuildDocument();
    }

    /**
     * Sets the view mode and rebuilds the document.
     */
    public void setViewMode(ViewMode mode) {
        viewModeProperty.set(mode);
    }

    public ViewMode getViewMode() {
        return viewModeProperty.get();
    }

    public ObjectProperty<ViewMode> viewModeProperty() {
        return viewModeProperty;
    }

    /**
     * Sets the handler for change selection events.
     */
    public void setOnChangeSelected(javafx.event.EventHandler<ChangeSelectionEvent> handler) {
        onChangeSelectedProperty.set(handler);
    }

    /**
     * Gets the handler for change selection events.
     */
    public javafx.event.EventHandler<ChangeSelectionEvent> getOnChangeSelected() {
        return onChangeSelectedProperty.get();
    }

    public ObjectProperty<javafx.event.EventHandler<ChangeSelectionEvent>> onChangeSelectedProperty() {
        return onChangeSelectedProperty;
    }

    /**
     * Finds the change at the given text position in the rendered view.
     * This maps the view position back to the original text position.
     */
    private Change findChangeAtPosition(int viewPosition) {
        for (RenderedChange renderedChange : renderedChanges) {
            if (viewPosition >= renderedChange.start() && viewPosition < renderedChange.end()) {
                return renderedChange.change();
            }
        }
        return null;
    }

    /**
     * Rebuilds the RichTextArea document with current changes and view mode.
     */
    private void rebuildDocument() {
        pendingSelectionId = selectedChangeId;
        RenderedDocument rendered = renderDocument(getViewMode(), true);
        renderedText = rendered.document().getText();
        renderedChanges = rendered.changes();
        getActionFactory().open(rendered.document()).execute(new ActionEvent());
    }

    private record RenderedDocument(Document document, List<RenderedChange> changes) { }

    /** Builds a clean result without selection or review decorations. */
    public Document getResultDocument() {
        return renderDocument(ViewMode.NO_MARKUP, false).document();
    }

    private RenderedDocument renderDocument(ViewMode mode, boolean markup) {
        List<DecorationModel> decorations = new ArrayList<>();
        StringBuilder viewText = new StringBuilder();
        List<RenderedChange> newRenderedChanges = new ArrayList<>();

        int oldIdx = 0;
        int newIdx = 0;

        List<Change> sortedChanges = new ArrayList<>(changes);
        sortedChanges.sort((a, b) -> Integer.compare(a.startOffset(), b.startOffset()));

        for (Change change : sortedChanges) {
            int changeStart = Math.max(oldIdx, Math.min(oldText.length(), change.startOffset()));
            int unchangedLength = changeStart - oldIdx;
            appendSourceSegment(oldDocument, oldText, oldIdx, unchangedLength, null, viewText, decorations);
            oldIdx += unchangedLength;
            newIdx = Math.min(newText.length(), newIdx + unchangedLength);

            String displayText = ChangeViewMode.getDisplayText(change, mode);
            int changeViewStart = viewText.length();
            switch (change.type()) {
                case INSERTED -> {
                    int sourceLength = Math.min(change.text().length(), newText.length() - newIdx);
                    appendChangedSourceSegment(newDocument, newText, newIdx, sourceLength,
                            change, displayText, newIdx, mode, markup, viewText, decorations);
                    newIdx += change.text().length();
                }
                case DELETED -> {
                    int sourceLength = Math.min(change.endOffset() - change.startOffset(), oldText.length() - oldIdx);
                    appendChangedSourceSegment(oldDocument, oldText, oldIdx, sourceLength,
                            change, displayText, oldIdx, mode, markup, viewText, decorations);
                    oldIdx += change.endOffset() - change.startOffset();
                }
                case FORMAT_CHANGED -> {
                    boolean showNewFormatting = mode != ViewMode.ORIGINAL
                            && change.status() != ChangeStatus.REJECTED;
                    Document sourceDocument = showNewFormatting ? newDocument : oldDocument;
                    String sourceText = showNewFormatting ? newText : oldText;
                    int sourceIndex = showNewFormatting ? newIdx : oldIdx;
                    int sourceLength = Math.min(change.text().length(), sourceText.length() - sourceIndex);
                    appendChangedSourceSegment(sourceDocument, sourceText, sourceIndex, sourceLength,
                            change, displayText, sourceIndex, mode, markup, viewText, decorations);
                    oldIdx += change.text().length();
                    newIdx += change.text().length();
                }
            }
            // Keep an anchor even when a change is hidden in Original/No Markup.
            newRenderedChanges.add(new RenderedChange(change, changeViewStart, viewText.length()));
        }
        appendSourceSegment(oldDocument, oldText, oldIdx, oldText.length() - oldIdx,
                null, viewText, decorations);

        return new RenderedDocument(new Document(viewText.toString(), decorations, 0),
                List.copyOf(newRenderedChanges));
    }

    private void appendSourceSegment(Document sourceDocument, String sourceText, int sourceStart,
                                     int length, Change change, StringBuilder viewText,
                                     List<DecorationModel> decorations) {
        appendSourceSegment(sourceDocument, sourceText, sourceStart, length, change,
                null, -1, viewText, decorations);
    }

    private void appendSourceSegment(Document sourceDocument, String sourceText, int sourceStart,
                                     int length, Change change, String displayText, int displaySourceStart,
                                     StringBuilder viewText, List<DecorationModel> decorations) {
        if (length <= 0 || sourceStart >= sourceText.length()) {
            return;
        }
        int end = Math.min(sourceText.length(), sourceStart + length);
        int cursor = sourceStart;
        List<DecorationModel> sourceDecorations = sourceDocument.getDecorations();
        int low = 0;
        int high = sourceDecorations.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            DecorationModel decoration = sourceDecorations.get(middle);
            if (decoration.getStart() + decoration.getLength() <= sourceStart) low = middle + 1;
            else high = middle;
        }
        for (int i = low; i < sourceDecorations.size(); i++) {
            DecorationModel sourceDecoration = sourceDecorations.get(i);
            if (sourceDecoration.getStart() >= end) break;
            int decorationEnd = sourceDecoration.getStart() + sourceDecoration.getLength();
            int overlapStart = Math.max(cursor, sourceDecoration.getStart());
            int overlapEnd = Math.min(end, decorationEnd);
            if (overlapStart >= overlapEnd) {
                continue;
            }
            if (cursor < overlapStart) {
                appendStyledSegment(sourceText, cursor, overlapStart, null, change,
                        displayText, displaySourceStart, viewText, decorations);
            }
            appendStyledSegment(sourceText, overlapStart, overlapEnd, sourceDecoration,
                    change, displayText, displaySourceStart, viewText, decorations);
            cursor = overlapEnd;
        }
        if (cursor < end) {
            appendStyledSegment(sourceText, cursor, end, null, change,
                    displayText, displaySourceStart, viewText, decorations);
        }
    }

    private void appendStyledSegment(String sourceText, int start, int end, DecorationModel sourceDecoration,
                                     Change change, String displayText, int displaySourceStart,
                                     StringBuilder viewText, List<DecorationModel> decorations) {
        String segment = sourceText.substring(start, end);
        int outputStart = viewText.length();
        if (displayText == null) {
            viewText.append(segment);
        } else {
            int displayStart = Math.min(displayText.length(), start - displaySourceStart);
            int displayEnd = Math.min(displayText.length(), displayStart + segment.length());
            viewText.append(displayText, displayStart, displayEnd);
        }
        Decoration sourceStyle = sourceDecoration == null
                ? TextDecoration.builder().presets().build()
                : sourceDecoration.getDecoration();
        ParagraphDecoration paragraphStyle = sourceDecoration == null
                ? ParagraphDecoration.builder().presets().build()
                : sourceDecoration.getParagraphDecoration();
        Decoration displayStyle = change == null || sourceStyle instanceof ImageDecoration
                ? sourceStyle : createDecorationForChange(change, sourceStyle);
        decorations.add(new DecorationModel(outputStart, viewText.length() - outputStart,
                displayStyle, paragraphStyle));
    }

    private void appendChangedSourceSegment(Document sourceDocument, String sourceText, int sourceStart,
                                            int length, Change change, String displayText,
                                            int displaySourceStart, ViewMode mode, boolean markup, StringBuilder viewText,
                                            List<DecorationModel> decorations) {
        if (displayText.isEmpty()) {
            return;
        }
        boolean markupMode = mode == ViewMode.ALL_MARKUP;
        boolean shouldMark = markup && markupMode && change.status() == ChangeStatus.PENDING;
        appendSourceSegment(sourceDocument, sourceText, sourceStart, length,
                shouldMark ? change : null, displayText, displaySourceStart, viewText, decorations);
    }

    /**
     * Creates a TextDecoration for the given change type.
     */
    private TextDecoration createDecorationForChange(Change change, Decoration sourceStyle) {
        TextDecoration.Builder builder = sourceStyle instanceof TextDecoration textDecoration
                ? TextDecoration.builder().fromDecoration(textDecoration)
                : TextDecoration.builder().presets();

        switch (change.type()) {
            case INSERTED -> {
                builder.underline(true);
                builder.foreground("#008000");
                builder.background("#e2f0d9");
            }
            case DELETED -> {
                builder.strikethrough(true);
                builder.foreground("#c00000");
                builder.background("#fce4d6");
            }
            case FORMAT_CHANGED -> {
                builder.background("#fff2cc");
                builder.foreground("#7f6000");
            }
        }

        return builder.build();
    }

    /**
     * Selects the given change in the view (scrolls to it).
     */
    public void selectChange(Change change) {
        if (change == null) {
            return;
        }
        selectedChangeId = change.id();
        pendingSelectionId = change.id();
        applyPendingSelection();
    }

    private void applyPendingSelection() {
        if (pendingSelectionId == null || getDocument() == null
                || !renderedText.equals(getDocument().getText())
                || !(getSkin() instanceof RichTextAreaSkin skin)) {
            return;
        }
        RichTextAreaViewModel viewModel = skin.getViewModel();
        if (viewModel == null) {
            return;
        }
        for (RenderedChange renderedChange : renderedChanges) {
            if (renderedChange.change().id().equals(pendingSelectionId)) {
                com.gluonhq.richtextarea.Selection selection = viewModel.getTextLength() == 0
                        ? new com.gluonhq.richtextarea.Selection(0, 0)
                        : viewModel.getTextBuffer().getInternalSelection(
                                new com.gluonhq.richtextarea.Selection(renderedChange.start(), renderedChange.end()));
                viewModel.setCaretPosition(selection.getStart());
                if (getViewMode() == ViewMode.ALL_MARKUP || getViewMode() == ViewMode.SIMPLE_MARKUP) {
                    viewModel.setSelection(selection);
                } else {
                    viewModel.setSelection(com.gluonhq.richtextarea.Selection.UNDEFINED);
                }
                pendingSelectionId = null;
                requestFocus();
                return;
            }
        }
    }

    /**
     * Event fired when a change is selected in the inline view.
     */
    public static class ChangeSelectionEvent extends Event {
        public static final EventType<ChangeSelectionEvent> ANY = new EventType<>(Event.ANY, "CHANGE_SELECTED");
        private static final long serialVersionUID = 1L;
        private transient final Change change;

        public ChangeSelectionEvent(Change change) {
            super(ANY);
            this.change = change;
        }

        public Change getChange() {
            return change;
        }
    }

    /**
     * Fires a change selection event.
     */
    private void fireChangeSelected(Change change) {
        javafx.event.EventHandler<ChangeSelectionEvent> handler = onChangeSelectedProperty.get();
        if (handler != null) {
            handler.handle(new ChangeSelectionEvent(change));
        }
    }
}