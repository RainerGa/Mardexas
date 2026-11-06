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

package de.richmarkdown.markdown;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD: Formatierungspr\u00fcf (AddFeatures #16).
 */
class MarkdownCheckerTest {

    @Test
    void emptyDocumentYieldsNoIssues() {
        assertTrue(MarkdownChecker.check(null).isEmpty());
        assertTrue(MarkdownChecker.check("").isEmpty());
        assertTrue(MarkdownChecker.check("   ").isEmpty());
    }

    @Test
    void validDocumentYieldsNoIssues() {
        String markdown = """
                # \u00dcberschrift

                Fett und *kursiv* sowie `Code`.

                [Link](https://example.com)

                ![Bild](https://example.com/bild.png)

                | Spalte 1 | Spalte 2 |
                | --- | --- |
                | Wert A | Wert B |
                """;
        assertTrue(MarkdownChecker.check(markdown).isEmpty());
    }

    @Test
    void unclosedCodeFenceIsError() {
        String markdown = "```\nCode ohne Schliessung";
        List<MarkdownIssue> issues = MarkdownChecker.check(markdown);
        assertEquals(1, issues.size());
        assertEquals(MarkdownIssue.Severity.ERROR, issues.get(0).severity());
        assertTrue(issues.get(0).message().contains("Code-Fence"));
        assertEquals(0, issues.get(0).position());
        assertEquals(1, issues.get(0).line());
    }

    @Test
    void emptyLinkTextIsError() {
        String markdown = "Text [](https://example.com) Ende";
        List<MarkdownIssue> issues = MarkdownChecker.check(markdown);
        assertEquals(1, issues.size());
        assertEquals(MarkdownIssue.Severity.ERROR, issues.get(0).severity());
        assertTrue(issues.get(0).message().contains("Link"));
    }

    @Test
    void emptyLinkUrlIsError() {
        String markdown = "Text [Link]() Ende";
        List<MarkdownIssue> issues = MarkdownChecker.check(markdown);
        assertEquals(1, issues.size());
        assertEquals(MarkdownIssue.Severity.ERROR, issues.get(0).severity());
    }

    @Test
    void imageWithoutAltIsWarning() {
        String markdown = "![](https://example.com/bild.png)";
        List<MarkdownIssue> issues = MarkdownChecker.check(markdown);
        assertEquals(1, issues.size());
        assertEquals(MarkdownIssue.Severity.WARNING, issues.get(0).severity());
        assertTrue(issues.get(0).message().contains("Bild"));
    }

    @Test
    void imageWithoutUrlIsWarning() {
        String markdown = "![Alt-Text]()";
        List<MarkdownIssue> issues = MarkdownChecker.check(markdown);
        assertEquals(1, issues.size());
        assertEquals(MarkdownIssue.Severity.WARNING, issues.get(0).severity());
    }

    @Test
    void unbalancedEmphasisIsWarning() {
        String markdown = "Text mit *ungerader Betonung";
        List<MarkdownIssue> issues = MarkdownChecker.check(markdown);
        assertEquals(1, issues.size());
        assertEquals(MarkdownIssue.Severity.WARNING, issues.get(0).severity());
        assertTrue(issues.get(0).message().contains("emphasis"));
    }

    @Test
    void balancedEmphasisYieldsNoIssue() {
        String markdown = "*kursiv* und **fett** sind balanced";
        assertTrue(MarkdownChecker.check(markdown).isEmpty());
    }

    @Test
    void tableWithUnequalColumnsIsWarning() {
        String markdown = "| A | B |\n| --- | --- |\n| nur eine Zelle |";
        List<MarkdownIssue> issues = MarkdownChecker.check(markdown);
        assertEquals(1, issues.size());
        assertEquals(MarkdownIssue.Severity.WARNING, issues.get(0).severity());
        assertTrue(issues.get(0).message().contains("Tabelle"));
    }

    @Test
    void headingJumpIsHint() {
        String markdown = "# H1\n\n### H3";
        List<MarkdownIssue> issues = MarkdownChecker.check(markdown);
        assertEquals(1, issues.size());
        assertEquals(MarkdownIssue.Severity.HINT, issues.get(0).severity());
        assertTrue(issues.get(0).message().contains("\u00dcberschriften-Sprung"));
    }

    @Test
    void allIssuesHaveValidPositionAndCoordinates() {
        String markdown = "```\n[](x)\n![](y)\n![alt]()\n*odd\n| a | b |\n| --- |\n| c | d | e |\n# H1\n\n### H3";
        List<MarkdownIssue> issues = MarkdownChecker.check(markdown);
        assertFalse(issues.isEmpty());
        for (MarkdownIssue issue : issues) {
            assertTrue(issue.position() >= 0);
            assertTrue(issue.line() >= 1);
            assertTrue(issue.column() >= 1);
        }
    }
}
