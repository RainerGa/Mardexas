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

package de.richmarkdown.editor;

import com.gluonhq.richtextarea.RichTextArea;
import de.richmarkdown.FxTestUtil;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * AP-01: {@link MarkdownEditor} übernimmt ein externes {@link RichTextArea}
 * vollständig (Link-Allowlist, Enter-Verhalten, Underline-Accelerator).
 */
class MarkdownEditorExternalControlTest {

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    private void flush() {
        FxTestUtil.flushFxEvents();
    }

    @Test
    void externalControlIsReturnedByGetControl() {
        RichTextArea external = FxTestUtil.callAndWait(RichTextArea::new);
        MarkdownEditor editor = FxTestUtil.callAndWait(() -> new MarkdownEditor(external));

        assertSame(external, editor.getControl());
    }

    @Test
    void formattingWorksOnExternalControl() {
        RichTextArea[] holder = new RichTextArea[1];
        MarkdownEditor[] editorHolder = new MarkdownEditor[1];
        FxTestUtil.runAndWait(() -> {
            MarkdownEditor created = new MarkdownEditor(new RichTextArea());
            holder[0] = created.getControl();
            editorHolder[0] = created;
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(created.getControl()), 600, 400));
            stage.show();
        });
        flush();
        MarkdownEditor editor = editorHolder[0];
        RichTextArea rta = holder[0];

        FxTestUtil.runAndWait(() -> editor.setMarkdown("hello"));
        flush();
        flush();
        FxTestUtil.runAndWait(() ->
                rta.getActionFactory().selectAll().execute(new javafx.event.ActionEvent()));
        flush();

        FxTestUtil.runAndWait(editor::toggleBold);
        flush();

        String markdown = FxTestUtil.callAndWait(editor::getMarkdown);
        assertEquals("**hello**", markdown);
    }

    @Test
    void linkCallbackFactoryIsInstalledOnExternalControl() {
        RichTextArea external = FxTestUtil.callAndWait(RichTextArea::new);
        FxTestUtil.callAndWait(() -> new MarkdownEditor(external));

        assertNotNull(FxTestUtil.callAndWait(external::getLinkCallbackFactory));
    }

    @Test
    void nullControlThrowsNullPointerException() {
        try {
            FxTestUtil.callAndWait(() -> new MarkdownEditor(null));
            throw new AssertionError("expected NullPointerException");
        } catch (RuntimeException wrapped) {
            if (!(wrapped.getCause() instanceof NullPointerException)) {
                throw wrapped;
            }
        }
    }
}
