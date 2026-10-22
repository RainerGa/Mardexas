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

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Positionsstack für die Zurück-/Vorwärts-Navigation (AddFeatures #4,
 * Vorbild LibreOffice Writer: „Zurück zur letzten Änderungsstelle“).
 *
 * <p>Die Klasse ist reine Logik ohne JavaFX-Szenenbezug und damit ohne
 * Toolkit startbar testbar. Sie merkt sich Caret-Positionen, zu denen
 * gesprungen wurde, und erlaubt das Navigieren hin und zurück wie in einem
 * Browser bzw. in der LO-Navigationsleiste:
 *
 * <ul>
 *   <li>{@link #jumpPerformed(int)} meldet einen Sprung zur Zielposition;
 *       die vorherige Position wird auf dem Zurück-Stack gemerkt, der
 *       Vorwärts-Stack wird geleert.</li>
 *   <li>{@link #goBack(int)} liefert die letzte Sprungquelle; die aktuelle
 *       Position wird dafür auf den Vorwärts-Stack gelegt.</li>
 *   <li>{@link #goForward(int)} spiegelt {@link #goBack(int)}.</li>
 * </ul>
 *
 * <p>Die verfügbaren Richtungen werden als JavaFX-Properties veröffentlicht,
 * damit Menüeinträge und Toolbar-Schaltflächen ihre Disable-Bindings direkt
 * anbinden können.
 */
public class NavigationHistory {

    /** Maximale Tiefe des Zurück-/Vorwärts-Stacks (LO-artig begrenzt). */
    public static final int MAX_DEPTH = 50;

    private final Deque<Integer> back = new ArrayDeque<>();
    private final Deque<Integer> forward = new ArrayDeque<>();
    private final ReadOnlyBooleanWrapper canGoBack = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper canGoForward = new ReadOnlyBooleanWrapper(false);

    /**
     * Meldet einen Sprung zur Zielposition.
     *
     * <p>Die bislang aktuelle Position wird auf dem Zurück-Stack gemerkt;
     * ein erneuter Sprung von derselben Position aus wird nicht doppelt
     * gemerkt. Der Vorwärts-Stack wird wie in Browsern geleert, da die
     * alte Vorwärtskette dann gegenstandslos ist.
     *
     * @param currentPosition Caret-Position, von der aus gesprungen wurde
     */
    public void jumpPerformed(int currentPosition) {
        if (!back.isEmpty() && back.peek() == currentPosition) {
            return;
        }
        back.push(currentPosition);
        while (back.size() > MAX_DEPTH) {
            back.removeLast();
        }
        forward.clear();
        updateProperties();
    }

    /**
     * Springt zurück zur letzten Sprungquelle.
     *
     * @param currentPosition aktuelle Caret-Position (wird für die
     *                        Vorwärts-Navigation gemerkt)
     * @return Zielposition oder leer, wenn kein Zurück-Sprung möglich ist
     */
    public java.util.Optional<Integer> goBack(int currentPosition) {
        if (back.isEmpty()) {
            return java.util.Optional.empty();
        }
        int target = back.pop();
        forward.push(currentPosition);
        updateProperties();
        return java.util.Optional.of(target);
    }

    /**
     * Springt vorwärts zur nächsten gemerkten Position.
     *
     * @param currentPosition aktuelle Caret-Position (wird für die
     *                        Zurück-Navigation gemerkt)
     * @return Zielposition oder leer, wenn kein Vorwärts-Sprung möglich ist
     */
    public java.util.Optional<Integer> goForward(int currentPosition) {
        if (forward.isEmpty()) {
            return java.util.Optional.empty();
        }
        int target = forward.pop();
        back.push(currentPosition);
        updateProperties();
        return java.util.Optional.of(target);
    }

    /**
     * Gibt an, ob ein Zurück-Sprung möglich ist.
     *
     * @return {@code true}, wenn mindestens eine Sprungquelle gemerkt ist
     */
    public boolean canGoBack() {
        return canGoBack.get();
    }

    /**
     * Gibt an, ob ein Vorwärts-Sprung möglich ist.
     *
     * @return {@code true}, wenn mindestens eine Vorwärtsposition gemerkt ist
     */
    public boolean canGoForward() {
        return canGoForward.get();
    }

    /**
     * Property für die Disable-Bindings von Zurück-Schaltflächen.
     *
     * @return lesbare Property des Zurück-Sprung-Zustands
     */
    public ReadOnlyBooleanProperty canGoBackProperty() {
        return canGoBack.getReadOnlyProperty();
    }

    /**
     * Property für die Disable-Bindings von Vorwärts-Schaltflächen.
     *
     * @return lesbare Property des Vorwärts-Sprung-Zustands
     */
    public ReadOnlyBooleanProperty canGoForwardProperty() {
        return canGoForward.getReadOnlyProperty();
    }

    /** Veröffentlicht die Stack-Zustände an die gebundenen UI-Elemente. */
    private void updateProperties() {
        canGoBack.set(!back.isEmpty());
        canGoForward.set(!forward.isEmpty());
    }
}
