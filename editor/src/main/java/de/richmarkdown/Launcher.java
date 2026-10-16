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

package de.richmarkdown;

import javafx.application.Application;

/**
 * Startklasse für das ausführbare FAT-JAR ({@code Mardexas-*-fat.jar}).
 *
 * <p>Die eigentliche Anwendung ({@link RichMarkDownEditorApp}) erweitert
 * {@code javafx.application.Application}. Stünde deren {@code main}-Methode
 * direkt als {@code Main-Class} im Manifest, meldet der JavaFX-Launcher beim
 * Classpath-Start, dass die Application-Klasse auf dem Module-Path liegen
 * müsse. Diese Klasse erweitert kein JavaFX-API und delegiert lediglich an
 * {@link RichMarkDownEditorApp#main(String[])}, sodass
 * {@code java -jar Mardexas-*-fat.jar} auch ohne Module-Path startet.</p>
 *
 * <p>Im FAT-JAR liegen alle Laufzeitabhängigkeiten in einer Datei (eigene
 * Klassen und Ressourcen, RichTextArea-Fork, Emoji sowie JavaFX-Klassen und
 * native Bibliotheken für Linux, Windows und macOS). Auf dem Classpath greifen
 * keine JPMS-Exportprüfungen, daher ist das sonst nötige {@code --add-exports}
 * für das RichTextArea-ViewModel-Paket hier nicht erforderlich.</p>
 */
public final class Launcher {

    private Launcher() {
    }

    /**
     * Startet den Editor.
     *
     * @param args Startargumente, werden an die Anwendung weitergereicht
     */
    public static void main(String[] args) {
        Application.launch(RichMarkDownEditorApp.class, args);
    }
}
