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
 * View mode for track changes display (matching Word/LibreOffice Writer).
 */
public enum ViewMode {
    /** Show all changes inline with full markup (default). */
    ALL_MARKUP,
    /** Show the result with numbered change markers in the margin. */
    SIMPLE_MARKUP,
    /** Show the result; pending changes are included as proposed. */
    NO_MARKUP,
    /** Show the original document regardless of review decisions. */
    ORIGINAL
}