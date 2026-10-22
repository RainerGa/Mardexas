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

/**
 * Moduldeskriptor des RichMarkDownEditor.
 *
 * Basis ist das JavaFX-Control {@link com.gluonhq.richtextarea.RichTextArea}
 * (RichTextArea-Fork aus dem im Maven-Reactor eingebundenen Git-Submodul).
 * FXML wird nicht benötigt (reine Java-UI). Als Bibliothek (AP-06/API-Doku)
 * zusätzlich exportiert: {@code io} (Datei-Engine), {@code ui} (+
 * {@code ui.dialogs} als Default-Shell), {@code i18n} + {@code settings}
 * (globale Konfiguration), {@code findreplace} (Suchen & Ersetzen nach LO).
 * Kein {@code opens} (kein FXML/Reflection).
 */
module RichMarkDownEditor {
    requires transitive javafx.controls;
    requires transitive com.gluonhq.richtextarea;
    // java.awt.Desktop für das Öffnen von Links (RME-002)
    requires java.desktop;
    requires java.prefs;

    exports de.richmarkdown;
    exports de.richmarkdown.editor;
    exports de.richmarkdown.findreplace;
    exports de.richmarkdown.markdown;
    exports de.richmarkdown.io;
    exports de.richmarkdown.ui;
    exports de.richmarkdown.ui.dialogs;
    exports de.richmarkdown.i18n;
    exports de.richmarkdown.settings;
}
