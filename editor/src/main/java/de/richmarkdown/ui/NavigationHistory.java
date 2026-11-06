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
import java.util.Optional;

/**
 * Sprungverlauf f\u00fcr die Zur\u00fcck-/Vorw\u00e4rts-Navigation (AddFeatures #4,
 * Vorbild LibreOffice Writer: "Zur\u00fcck zur letzten \u00c4nderungsstelle").
 *
 * <p>Die Klasse ist reine Logik ohne JavaFX-Szenenbezug und damit ohne
 * Toolkit startbar testbar. Sie merkt sich Sprungziele (Label + Position +
 * Typ), zu denen gesprungen wurde, und erlaubt das Navigieren hin und zur\u00fcck
 * wie in einem Browser bzw. in der LO-Navigationsleiste:
 *
 * <ul>
 *   <li>{@link #jumpPerformed(int)} meldet einen Sprung zur Zielposition;
 *       die vorherige Position wird auf dem Zur\u00fcck-Stack gemerkt, der
 *       Vorw\u00e4rts-Stack wird geleert.</li>
 *   <li>{@link #jumpPerformed(int, String, JumpType)} erweitert den Sprung um
 *       ein beschriftendes Label und einen Typ (z. B. {@code HEADING} oder
 *       {@code ISSUE}), damit der Verlauf f\u00fcr TOC- und
 *       Pr\u00fcf-Ergebnis-Sprunge aufgezeichnet werden kann.</li>
 *   <li>{@link #goBack(int)} liefert das letzte Sprungziel; die aktuelle
 *       Position wird daf\u00fcr auf den Vorw\u00e4rts-Stack gelegt.</li>
 *   <li>{@link #goForward(int)} spiegelt {@link #goBack(int)}.</li>
 * </ul>
 *
 * <p>Die verf\u00fcgbaren Richtungen werden als JavaFX-Properties ver\u00f6ffentlicht,
 * damit Men\u00fceintr\u00e4ge und Toolbar-Schaltfl\u00e4chen ihre Disable-Bindings
 * direkt anbinden k\u00f6nnen.
 */
public class NavigationHistory {

    /** Maximale Tiefe des Zur\u00fcck-/Vorw\u00e4rts-Stacks (LO-artig begrenzt). */
    public static final int MAX_DEPTH = 50;

    private final Deque<JumpTarget> back = new ArrayDeque<>();
    private final Deque<JumpTarget> forward = new ArrayDeque<>();
    private final ReadOnlyBooleanWrapper canGoBack = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper canGoForward = new ReadOnlyBooleanWrapper(false);

    /**
     * Ein benannter Sprungverlaufseintrag: Label, Zielposition und Sprungtyp.
     *
     * @param label  beschriftendes Label des Sprungziels (kann {@code null} sein)
     * @param position Caret-Position des Sprungziels
     * @param type   Typ des Sprungziels
     */
    public record JumpTarget(String label, int position, JumpType type) {
        public JumpTarget {
            if (position < 0) {
                throw new IllegalArgumentException("Position darf nicht negativ sein, war: " + position);
            }
            if (type == null) {
                throw new NullPointerException("Sprungtyp darf nicht null sein");
            }
        }
    }

    /**
     * Art eines Sprungziels im Navigationsverlauf.
     */
    public enum JumpType {
        /** Roher Caret-Sprung (R\u00fcckgabe/Navigation ohne speziellen Kontext). */
        POSITION,
        /** Sprung zu einer Dokument-\u00dcberschrift (Dokumentstruktur-Ansicht). */
        HEADING,
        /** Sprung zu einem vom Formatierungspr\u00fcf melde Fundort. */
        ISSUE,
        /** Sprung zu einem Suchtreffer. */
        SEARCH
    }

    /**
     * Meldet einen Sprung zur Zielposition (R\u00fcckwärtskompatibilit\u00e4t).
     *
     * <p>Die bislang aktuelle Position wird auf dem Zur\u00fcck-Stack gemerkt;
     * ein erneuter Sprung von derselben Position aus wird nicht doppelt
     * gemerkt. Der Vorw\u00e4rts-Stack wird wie in Browsern geleert, da die
     * alte Vorw\u00e4rtskette dann gegenstandslos ist.
     *
     * @param currentPosition Caret-Position, von der aus gesprungen wurde
     */
    public void jumpPerformed(int currentPosition) {
        jumpPerformed(currentPosition, null, JumpType.POSITION);
    }

    /**
     * Meldet einen Sprung zur Zielposition mit benanntem Ziel.
     *
     * <p>Die bislang aktuelle Position wird auf dem Zur\u00fcck-Stack gemerkt;
     * ein erneuter Sprung von derselben Position aus wird nicht doppelt
     * gemerkt. Der Vorw\u00e4rts-Stack wird wie in Browsern geleert, da die
     * alte Vorw\u00e4rtskette dann gegenstandslos ist.
     *
     * @param currentPosition Caret-Position, von der aus gesprungen wurde
     * @param label           beschriftendes Label des Sprungziels
     * @param type            Typ des Sprungziels
     */
    public void jumpPerformed(int currentPosition, String label, JumpType type) {
        if (!back.isEmpty() && back.peek().position() == currentPosition) {
            return;
        }
        back.push(new JumpTarget(label, currentPosition, type));
        while (back.size() > MAX_DEPTH) {
            back.removeLast();
        }
        forward.clear();
        updateProperties();
    }

    /**
     * Springt zur\u00fcck zur letzten Sprungquelle.
     *
     * @param currentPosition aktuelle Caret-Position (wird f\u00fcr die
     *                        Vorw\u00e4rts-Navigation gemerkt)
     * @return Zielsprung oder leer, wenn kein Zur\u00fcck-Sprung m\u00f6glich ist
     */
    public Optional<JumpTarget> goBack(int currentPosition) {
        if (back.isEmpty()) {
            return Optional.empty();
        }
        JumpTarget target = back.pop();
        forward.push(new JumpTarget(null, currentPosition, JumpType.POSITION));
        updateProperties();
        return Optional.of(target);
    }

    /**
     * Springt vorw\u00e4rts zur n\u00e4chsten gemerkten Position.
     *
     * @param currentPosition aktuelle Caret-Position (wird f\u00fcr die
     *                        Zur\u00fcck-Navigation gemerkt)
     * @return Zielsprung oder leer, wenn kein Vorw\u00e4rts-Sprung m\u00f6glich ist
     */
    public Optional<JumpTarget> goForward(int currentPosition) {
        if (forward.isEmpty()) {
            return Optional.empty();
        }
        JumpTarget target = forward.pop();
        back.push(new JumpTarget(null, currentPosition, JumpType.POSITION));
        updateProperties();
        return Optional.of(target);
    }

    /**
     * Gibt an, ob ein Zur\u00fcck-Sprung m\u00f6glich ist.
     *
     * @return {@code true}, wenn mindestens eine Sprungquelle gemerkt ist
     */
    public boolean canGoBack() {
        return canGoBack.get();
    }

    /**
     * Gibt an, ob ein Vorw\u00e4rts-Sprung m\u00f6glich ist.
     *
     * @return {@code true}, wenn mindestens eine Vorw\u00e4rtsposition gemerkt ist
     */
    public boolean canGoForward() {
        return canGoForward.get();
    }

    /**
     * Property f\u00fcr die Disable-Bindings von Zur\u00fcck-Schaltfl\u00e4chen.
     *
     * @return lesbare Property des Zur\u00fcck-Sprung-Zustands
     */
    public ReadOnlyBooleanProperty canGoBackProperty() {
        return canGoBack.getReadOnlyProperty();
    }

    /**
     * Property f\u00fcr die Disable-Bindings von Vorw\u00e4rts-Schaltfl\u00e4chen.
     *
     * @return lesbare Property des Vorw\u00e4rts-Sprung-Zustands
     */
    public ReadOnlyBooleanProperty canGoForwardProperty() {
        return canGoForward.getReadOnlyProperty();
    }

    /** Ver\u00f6ffentlicht die Stack-Zust\u00e4nde an die gebundenen UI-Elemente. */
    private void updateProperties() {
        canGoBack.set(!back.isEmpty());
        canGoForward.set(!forward.isEmpty());
    }
}
