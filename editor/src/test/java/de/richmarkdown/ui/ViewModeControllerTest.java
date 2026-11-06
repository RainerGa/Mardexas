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
import de.richmarkdown.FxTestUtil;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für den ViewModeController (AddFeatures #16).
 * Pr�ft den Wechsel zwischen Fluss- und Seitenansicht.
 */
class ViewModeControllerTest {

    private RichTextArea editor;
    private ZoomController zoomController;
    private ViewModeController viewMode;
    private Stage stage;

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    @BeforeEach
    void setUp() {
        FxTestUtil.runAndWait(() -> {
            editor = new RichTextArea();
            zoomController = new ZoomController(editor);
            viewMode = new ViewModeController(editor, zoomController);
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(viewMode.getActiveView()), 800, 600));
            stage.show();
        });
    }

    @AfterAll
    static void tearDown() {
        // kein stage.hide(): blockt den FX-Thread in Monocle headless
    }

    @Test
    void initiallyShowsFlowView() {
        assertFalse(FxTestUtil.callAndWait(viewMode::isShowingPages),
                "initialer Modus ist Flussansicht");
        assertSame(editor, FxTestUtil.callAndWait(viewMode::getActiveView),
                "aktive Ansicht ist der Editor");
    }

    @Test
    void showPageViewSwitchesToPageView() {
        FxTestUtil.runAndWait(viewMode::showPageView);

        assertTrue(FxTestUtil.callAndWait(viewMode::isShowingPages),
                "nach showPageView() wird Seitenansicht angezeigt");
        assertNotSame(editor, FxTestUtil.callAndWait(viewMode::getActiveView),
                "aktive Ansicht ist nicht mehr der Editor");
        assertNotNull(FxTestUtil.callAndWait(viewMode::getPageView),
                "PageView ist verf�gbar");
    }

    @Test
    void showFlowViewSwitchesBackToFlowView() {
        FxTestUtil.runAndWait(viewMode::showPageView);
        assertTrue(FxTestUtil.callAndWait(viewMode::isShowingPages));

        FxTestUtil.runAndWait(viewMode::showFlowView);
        assertFalse(FxTestUtil.callAndWait(viewMode::isShowingPages),
                "nach showFlowView() wird wieder Flussansicht angezeigt");
        assertSame(editor, FxTestUtil.callAndWait(viewMode::getActiveView),
                "aktive Ansicht ist wieder der Editor");
    }

    @Test
    void pageViewHasEditorSet() {
        FxTestUtil.runAndWait(viewMode::showPageView);
        DocumentView pageView = FxTestUtil.callAndWait(viewMode::getPageView);
        assertSame(editor, FxTestUtil.callAndWait(pageView::getEditor),
                "PageView hat den Editor gesetzt");
    }

    @Test
    void repeatedShowPageViewIsIdempotent() {
        FxTestUtil.runAndWait(viewMode::showPageView);
        FxTestUtil.runAndWait(viewMode::showPageView);
        assertTrue(FxTestUtil.callAndWait(viewMode::isShowingPages),
                "wiederholter Aufruf von showPageView() bleibt in Seitenansicht");
    }

    @Test
    void repeatedShowFlowViewIsIdempotent() {
        FxTestUtil.runAndWait(viewMode::showFlowView);
        FxTestUtil.runAndWait(viewMode::showFlowView);
        assertFalse(FxTestUtil.callAndWait(viewMode::isShowingPages),
                "wiederholter Aufruf von showFlowView() bleibt in Flussansicht");
    }
}
