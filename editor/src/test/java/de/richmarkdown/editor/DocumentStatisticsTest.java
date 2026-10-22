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

package de.richmarkdown.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TDD: Dokumentstatistik nach dem Vorbild LO „Extras → Statistik“
 * (AddFeatures #3).
 */
class DocumentStatisticsTest {

    @Test
    void emptyStatisticsHasZeroCounts() {
        DocumentStatistics stats = DocumentStatistics.empty();
        assertEquals(0, stats.words());
        assertEquals(0, stats.charactersWithSpaces());
        assertEquals(0, stats.charactersWithoutSpaces());
        assertEquals(0, stats.spaces());
        assertEquals(0, stats.sentences());
        assertEquals(0, stats.paragraphs());
        assertEquals(0, stats.lines());
        assertEquals(0, stats.tables());
        assertEquals(0, stats.images());
    }

    @Test
    void emptyTextCountsNothing() {
        DocumentStatistics stats = DocumentStatistics.of("", 0, 0);
        assertEquals(0, stats.words());
        assertEquals(0, stats.paragraphs());
        assertEquals(0, stats.lines());
    }

    @Test
    void nullTextCountsNothing() {
        DocumentStatistics stats = DocumentStatistics.of(null, 0, 0);
        assertEquals(0, stats.words());
    }

    @Test
    void countsSimpleWords() {
        DocumentStatistics stats = DocumentStatistics.of("Hallo Welt", 0, 0);
        assertEquals(2, stats.words());
        assertEquals(10, stats.charactersWithSpaces());
        assertEquals(9, stats.charactersWithoutSpaces());
        assertEquals(1, stats.spaces());
        assertEquals(1, stats.paragraphs());
        assertEquals(1, stats.lines());
    }

    @Test
    void countsMultipleLines() {
        DocumentStatistics stats = DocumentStatistics.of("eins\ndrei\nfuenf", 0, 0);
        assertEquals(3, stats.lines());
        assertEquals(3, stats.paragraphs());
        assertEquals(3, stats.words());
    }

    @Test
    void countsTabsAsSpacesButNotLineBreaks() {
        // \t zählt als Leerzeichen und trennt Wörter, \n ist Absatzmarke
        DocumentStatistics stats = DocumentStatistics.of("a\tb\nc", 0, 0);
        assertEquals(1, stats.spaces());
        assertEquals(3, stats.words());
        assertEquals(4, stats.charactersWithSpaces()); // a, \t, b, c (ohne \n)
        assertEquals(3, stats.charactersWithoutSpaces());
    }

    @Test
    void countsSentencesAtSentenceEndCharacters() {
        DocumentStatistics stats = DocumentStatistics.of("Satz eins. Satz zwei!", 0, 0);
        assertEquals(2, stats.sentences());
    }

    @Test
    void textWithoutSentenceEndCountsAsOneSentence() {
        DocumentStatistics stats = DocumentStatistics.of("Kein Satzende", 0, 0);
        assertEquals(1, stats.sentences());
    }

    @Test
    void trailingSentenceEndAddsNoExtraSentence() {
        DocumentStatistics stats = DocumentStatistics.of("Fertig.", 0, 0);
        assertEquals(1, stats.sentences());
    }

    @Test
    void countsTablesAndImages() {
        DocumentStatistics stats = DocumentStatistics.of("Text", 2, 3);
        assertEquals(2, stats.tables());
        assertEquals(3, stats.images());
    }

    @Test
    void stripsNonVisiblePlaceholderCharacters() {
        // Bild-Platzhalter (U+FFFC) und Tabellen-Trenner (U+200B) zählen nicht;
        // mit Leerzeichen dazwischen entstehen zwei Wörter
        DocumentStatistics stats = DocumentStatistics.of("a\uFFFC \u200Bb", 0, 0);
        assertEquals(3, stats.charactersWithSpaces()); // a, Leerzeichen, b
        assertEquals(2, stats.words());
        assertEquals(1, stats.spaces());
    }

    @Test
    void negativeTablesOrImagesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> DocumentStatistics.of("x", -1, 0));
        assertThrows(IllegalArgumentException.class, () -> DocumentStatistics.of("x", 0, -1));
    }

    @Test
    void punctuationAloneDoesNotCreateWords() {
        // Zählregel wie LO: Wörter werden nur durch Whitespace getrennt
        DocumentStatistics stats = DocumentStatistics.of("Wort,weil", 0, 0);
        assertEquals(1, stats.words());
        assertEquals(9, stats.charactersWithoutSpaces());
    }
}
