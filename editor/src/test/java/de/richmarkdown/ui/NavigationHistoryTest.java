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

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD: Zur\u00fcck-/Vorw\u00e4rts-Navigation (AddFeatures #4, Vorbild LO-
 * Navigationsleiste "Zur\u00fcck zur letzten \u00c4nderungsstelle").
 *
 * <p>Die \u00fcberarbeitete Historie speichert benannte Sprungziele
 * ({@link NavigationHistory.JumpTarget}) statt roher Positionen.
 */
class NavigationHistoryTest {

    private final NavigationHistory history = new NavigationHistory();

    @Test
    void nothingNavigableInitially() {
        assertFalse(history.canGoBack());
        assertFalse(history.canGoForward());
        assertTrue(history.goBack(0).isEmpty());
        assertTrue(history.goForward(0).isEmpty());
    }

    @Test
    void jumpMakesSourceNavigableBack() {
        history.jumpPerformed(10);
        assertTrue(history.canGoBack());
        assertFalse(history.canGoForward());
        Optional<NavigationHistory.JumpTarget> back = history.goBack(30);
        assertTrue(back.isPresent());
        assertEquals(10, back.get().position());
        assertEquals(NavigationHistory.JumpType.POSITION, back.get().type());
        assertNull(back.get().label());
    }

    @Test
    void goBackMakesTargetNavigableForward() {
        history.jumpPerformed(10);
        history.goBack(30);
        assertFalse(history.canGoBack());
        assertTrue(history.canGoForward());
        Optional<NavigationHistory.JumpTarget> forward = history.goForward(10);
        assertTrue(forward.isPresent());
        assertEquals(30, forward.get().position());
    }

    @Test
    void forwardRestoresAndResetsForwardStack() {
        history.jumpPerformed(10);
        history.goBack(30);
        assertEquals(30, history.goForward(10).get().position());
        assertFalse(history.canGoForward());
        assertTrue(history.canGoBack());
    }

    @Test
    void newJumpClearsForwardStack() {
        history.jumpPerformed(10);
        history.goBack(30); // Vorw\u00e4rts: 30
        history.jumpPerformed(5); // neuer Sprung von 10 aus
        assertFalse(history.canGoForward());
    }

    @Test
    void jumpFromSamePositionIsNotRecordedTwice() {
        history.jumpPerformed(10);
        history.jumpPerformed(10); // Duplikat
        history.goBack(0);
        assertFalse(history.canGoBack(), "kein zweiter Eintrag derselben Quellposition");
    }

    @Test
    void multipleJumpsNavigateInReverseOrder() {
        history.jumpPerformed(1);
        history.jumpPerformed(2);
        history.jumpPerformed(3);
        assertEquals(3, history.goBack(99).get().position());
        assertEquals(2, history.goBack(3).get().position());
        assertEquals(1, history.goBack(2).get().position());
        assertFalse(history.canGoBack());
    }

    @Test
    void depthIsLimited() {
        for (int i = 0; i < NavigationHistory.MAX_DEPTH + 20; i++) {
            history.jumpPerformed(i);
        }
        int navigated = 0;
        while (history.goBack(0).isPresent()) {
            navigated++;
        }
        assertEquals(NavigationHistory.MAX_DEPTH, navigated);
    }

    @Test
    void propertiesReflectStackState() {
        assertFalse(history.canGoBackProperty().get());
        history.jumpPerformed(1);
        assertTrue(history.canGoBackProperty().get());
        history.goBack(2);
        assertTrue(history.canGoForwardProperty().get());
    }

    @Test
    void jumpWithLabelAndTypeIsPreserved() {
        history.jumpPerformed(5, "Erste \u00dcberschrift", NavigationHistory.JumpType.HEADING);
        Optional<NavigationHistory.JumpTarget> target = history.goBack(99);
        assertTrue(target.isPresent());
        assertNotNull(target.get().label());
        assertEquals("Erste \u00dcberschrift", target.get().label());
        assertEquals(NavigationHistory.JumpType.HEADING, target.get().type());
        assertEquals(5, target.get().position());
    }

    @Test
    void goBackRecordsCurrentOnForwardStack() {
        history.jumpPerformed(10, "Quelle", NavigationHistory.JumpType.SEARCH);
        Optional<NavigationHistory.JumpTarget> target = history.goBack(40);
        assertTrue(target.isPresent());
        assertEquals(10, target.get().position());
        Optional<NavigationHistory.JumpTarget> forward = history.goForward(10);
        assertTrue(forward.isPresent());
        assertEquals(40, forward.get().position());
        assertEquals(NavigationHistory.JumpType.POSITION, forward.get().type());
    }
}
