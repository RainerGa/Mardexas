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


/**
 * Utility for view mode logic on changes.
 * Separated to avoid circular dependency between markdown and ui packages.
 */
public final class ChangeViewMode {

    private ChangeViewMode() {
    }

    /**
     * Returns true if this change should be visible in the given view mode.
     */
    public static boolean isVisibleIn(Change change, ViewMode mode) {
        return !getDisplayText(change, mode).isEmpty();
    }

    /**
     * Returns the text to display for this change in the given view mode.
     */
    public static String getDisplayText(Change change, ViewMode mode) {
        return switch (mode) {
            case ALL_MARKUP -> switch (change.type()) {
                case INSERTED -> change.status() == ChangeStatus.REJECTED ? "" : change.text();
                case DELETED -> change.status() == ChangeStatus.ACCEPTED ? "" : change.text();
                case FORMAT_CHANGED -> change.text();
            };
            case SIMPLE_MARKUP, NO_MARKUP -> {
                if (change.type() == ChangeType.INSERTED && change.status() != ChangeStatus.REJECTED) yield change.text();
                if (change.type() == ChangeType.DELETED && change.status() == ChangeStatus.REJECTED) yield change.text();
                if (change.type() == ChangeType.FORMAT_CHANGED) yield change.text();
                yield "";
            }
            case ORIGINAL -> {
                if (change.type() == ChangeType.DELETED || change.type() == ChangeType.FORMAT_CHANGED) {
                    yield change.text();
                }
                yield "";
            }
        };
    }
}