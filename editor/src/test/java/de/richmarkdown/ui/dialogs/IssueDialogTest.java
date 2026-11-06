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

package de.richmarkdown.ui.dialogs;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.markdown.MarkdownIssue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD: Formatfehler-Dialog (AddFeatures #16).
 */
class IssueDialogTest {

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    @AfterAll
    static void tearDown() {
        // kein hide(): blockt den FX-Thread in Monocle headless
    }

    @Test
    void showsIssuesFromSupplier() {
        List<MarkdownIssue> issues = List.of(
                new MarkdownIssue(MarkdownIssue.Severity.ERROR, "Ungeschlossener Code-Fence", 0, 1, 1),
                new MarkdownIssue(MarkdownIssue.Severity.WARNING, "Bild ohne Alt-Text", 5, 1, 6),
                new MarkdownIssue(MarkdownIssue.Severity.HINT, "\u00dcberschriften-Sprung", 10, 3, 1));

        IssueDialog dialog = FxTestUtil.callAndWait(() ->
                new IssueDialog(() -> issues, pos -> {}));
        assertEquals(3, dialog.getIssues().size());
        assertEquals(MarkdownIssue.Severity.ERROR, dialog.getIssues().get(0).severity());
        assertEquals(MarkdownIssue.Severity.HINT, dialog.getIssues().get(2).severity());
    }

    @Test
    void clickTriggersJumpHandler() {
        AtomicInteger jumped = new AtomicInteger(-1);
        List<MarkdownIssue> issues = List.of(
                new MarkdownIssue(MarkdownIssue.Severity.ERROR, "Issue eins", 42, 2, 5));

        IssueDialog dialog = FxTestUtil.callAndWait(() ->
                new IssueDialog(() -> issues, jumped::set));

        FxTestUtil.runAndWait(() -> dialog.getListView().getSelectionModel().select(0));
        assertEquals(42, jumped.get());
    }

    @Test
    void checkButtonRefreshes() {
        List<MarkdownIssue> issues = List.of(
                new MarkdownIssue(MarkdownIssue.Severity.ERROR, "Issue eins", 1, 1, 1));
        IssueDialog dialog = FxTestUtil.callAndWait(() ->
                new IssueDialog(() -> issues, pos -> {}));
        assertEquals(1, dialog.getIssues().size());

        assertNotNull(FxTestUtil.callAndWait(dialog::getCheckButton));
        assertNotNull(FxTestUtil.callAndWait(() ->
                dialog.getDialogPane().lookupButton(DialogButtons.CLOSE)));
    }
}
