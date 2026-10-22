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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD: Zurück-/Vorwärts-Navigation (AddFeatures #4, Vorbild LO-
 * Navigationsleiste „Zurück zur letzten Änderungsstelle“).
 */
class NavigationHistoryTest {

    private final NavigationHistory history = new NavigationHistory();

    @Test
    void nothingNavigableInitially() {
        assertFalse(history.canGoBack());
        assertFalse(history.canGoForward());
        assertEquals(Optional.empty(), history.goBack(0));
        assertEquals(Optional.empty(), history.goForward(0));
    }

    @Test
    void jumpMakesSourceNavigableBack() {
        history.jumpPerformed(10);
        assertTrue(history.canGoBack());
        assertFalse(history.canGoForward());
        assertEquals(Optional.of(10), history.goBack(30));
    }

    @Test
    void goBackMakesTargetNavigableForward() {
        history.jumpPerformed(10);
        history.goBack(30);
        assertFalse(history.canGoBack());
        assertTrue(history.canGoForward());
        assertEquals(Optional.of(30), history.goForward(10));
    }

    @Test
    void forwardRestoresAndResetsForwardStack() {
        history.jumpPerformed(10);
        history.goBack(30);
        assertEquals(Optional.of(30), history.goForward(10));
        assertFalse(history.canGoForward());
        assertTrue(history.canGoBack());
    }

    @Test
    void newJumpClearsForwardStack() {
        history.jumpPerformed(10);
        history.goBack(30); // Vorwärts: 30
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
        assertEquals(Optional.of(3), history.goBack(99));
        assertEquals(Optional.of(2), history.goBack(3));
        assertEquals(Optional.of(1), history.goBack(2));
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
}
