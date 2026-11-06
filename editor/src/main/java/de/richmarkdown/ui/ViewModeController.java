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

import com.gluonhq.richtextarea.DocumentView;
import com.gluonhq.richtextarea.RichTextArea;
import javafx.scene.Node;

/**
 * Switches the same editable document between the flowing editor and page layout.
 */
public final class ViewModeController {

    private final RichTextArea editor;
    private final ZoomController zoomController;
    private final DocumentView pageView = new DocumentView();
    private final EditorPresentation flowPresentation;
    private boolean showingPages;

    public ViewModeController(RichTextArea editor, ZoomController zoomController) {
        this.editor = editor;
        this.zoomController = zoomController;
        this.flowPresentation = EditorPresentation.capture(editor);
        zoomController.zoomProperty().addListener((obs, oldZoom, newZoom) -> {
            if (showingPages) {
                pageView.setZoom(newZoom.doubleValue() / 100.0);
            }
        });
        editor.pageFormatProperty().addListener((obs, oldFormat, newFormat) -> {
            if (showingPages) {
                pageView.setPageFormat(newFormat);
            }
        });
        editor.headersFootersProperty().addListener((obs, oldHeadersFooters, newHeadersFooters) -> {
            if (showingPages) {
                pageView.setHeadersFooters(newHeadersFooters);
            }
        });
    }

    public void showFlowView() {
        if (showingPages) {
            pageView.setEditor(null);
            flowPresentation.restore(editor);
            zoomController.setView(editor);
            showingPages = false;
        }
        editor.requestFocus();
    }

    public void showPageView() {
        if (!showingPages) {
            zoomController.setView(pageView);
            pageView.setEditor(editor);
            pageView.setPageFormat(editor.getPageFormat());
            pageView.setHeadersFooters(editor.getHeadersFooters());
            pageView.setZoom(zoomController.getZoom() / 100.0);
            showingPages = true;
        }
        editor.requestFocus();
    }

    public boolean isShowingPages() {
        return showingPages;
    }

    public Node getActiveView() {
        return showingPages ? pageView : editor;
    }

    public DocumentView getPageView() {
        return pageView;
    }

    /**
     * Erfasst und stellt den Darstellungszustand eines {@link RichTextArea}
     * temporär sicher, damit er nach einem View-Wechsel wiederhergestellt
     * werden kann (z. B. beim Wechsel zwischen Fluss- und Seitenansicht).
     *
     * @param managed         ob das Control vom Parent verwaltet wird
     * @param visible         ob das Control sichtbar ist
     * @param opacity         Deckkraft des Controls
     * @param mouseTransparent ob das Control Mausereignisse durchlässt
     * @param focusTraversable ob das Control den Fokus erhalten kann
     */
    private record EditorPresentation(boolean managed, boolean visible, double opacity,
                                      boolean mouseTransparent, boolean focusTraversable) {

        /**
         * Nimmt den aktuellen Darstellungszustand des Editors auf.
         *
         * @param editor der zu erfassende Editor
         * @return Snapshot des Darstellungszustands
         */
        static EditorPresentation capture(RichTextArea editor) {
            return new EditorPresentation(editor.isManaged(), editor.isVisible(), editor.getOpacity(),
                    editor.isMouseTransparent(), editor.isFocusTraversable());
        }

        /**
         * Stellt den zuvor erfassten Darstellungszustand am Editor wieder her.
         *
         * @param editor der Editor, auf den der Zustand angewendet wird
         */
        void restore(RichTextArea editor) {
            editor.setManaged(managed);
            editor.setVisible(visible);
            editor.setOpacity(opacity);
            editor.setMouseTransparent(mouseTransparent);
            editor.setFocusTraversable(focusTraversable);
        }
    }
}
