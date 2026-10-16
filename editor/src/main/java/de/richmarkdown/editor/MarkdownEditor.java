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

import com.gluonhq.richtextarea.RichTextArea;
import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.model.DecorationModel;
import com.gluonhq.richtextarea.model.ParagraphDecoration;
import com.gluonhq.richtextarea.model.TableDecoration;
import com.gluonhq.richtextarea.model.TextDecoration;
import com.gluonhq.richtextarea.viewmodel.RichTextAreaViewModel;
import de.richmarkdown.markdown.MarkdownStyles;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.event.ActionEvent;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Fassade über {@link RichTextArea} – die einzige Stelle der Anwendung, die das
 * Control kennt (PROJEKTPLAN 5.2).
 *
 * <p>Kapselt:
 * <ul>
 *   <li>Selektions-basierte Textformatierung (F/K/U/S) wie in Word</li>
 *   <li>Absatzformate (Überschriften, Zitat, Listen, Einzug) über die ActionFactory</li>
 *   <li>das vollständige Enter-Regelwerk (4.5) über den {@code onAction}-Hook</li>
 *   <li>den eigenen Strg+U-Accelerator (in rich-text-area 1.2.6 nicht builtin)</li>
 * </ul>
 *
 * <p>Öffentliche Dokument- und Formatierungsoperationen sind für den JavaFX-
 * Application Thread vorgesehen. Sie laufen über die {@code ActionFactory} und sind damit
 * undo-fähige Einzel-Commands. Für die Absatz-Analyse im Enter-Handler wird der
 * {@link RichTextAreaViewModel} der Skin verwendet (Paket nicht exportiert →
 * {@code --add-exports} in pom.xml, siehe Kap. 0 des Projektplans).
 */
public class MarkdownEditor {

    private final RichTextArea control;

    /**
     * Listener für Dokumentänderungen. Die Bibliothek bietet keine öffentliche
     * Beobachtung von Dekorationsänderungen – daher meldet die Fassade jede
     * eigene Operation. Das Feuern erfolgt über {@code Platform.runLater},
     * damit es **nach** der asynchronen Action läuft (für die Strukturansicht).
     */
    private final List<Runnable> changeListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Leerzeichenlauf für die HR-Abbildung (Writer erkennt „nur Leerzeichen + strikethrough"). */
    private static final String HR_SPACES = "          ";

    /**
     * Erlaubte URL-Schemata für Hyperlinks (RME-002). Dokument-URLs sind nicht
     * vertrauenswürdig: {@code file:}, {@code javascript:}, {@code data:} oder
     * registrierte Protokoll-Handler ({@code ms-msdt:}, {@code search-ms:}, …)
     * dürfen nicht aus einem Dokument heraus aufgerufen werden (CWE-88/CWE-829).
     */
    private static final Set<String> ALLOWED_LINK_SCHEMES = Set.of("http", "https", "mailto");

    /**
     * Erzeugt die Fassade inklusive eigenem {@link RichTextArea}.
     * Das Control erhält seinen Skin, sobald es in einer angezeigten Szene liegt.
     */
    public MarkdownEditor() {
        this.control = new RichTextArea();
        // RME-002: eigener Link-Callback mit Schema-Allowlist. Die
        // Default-Factory der Bibliothek übergibt jede Dokument-URL ungeprüft
        // an xdg-open/rundll32 (Runtime.exec).
        this.control.setLinkCallbackFactory(
                node -> node instanceof javafx.scene.text.Text ? this::openExternalLink : null);
        installEnterBehavior();
        installUnderlineAccelerator();
    }

    /**
     * Öffnet einen Link ausschließlich für erlaubte Schemata (http/https/mailto)
     * über die Desktop-API. Alles andere wird stillschweigend verworfen.
     */
    private void openExternalLink(String url) {
        if (!isSafeLinkUrl(url)) {
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url.trim()));
        } catch (Exception e) {
            // Kein Handler verfügbar oder Öffnen fehlgeschlagen – ein Klick auf
            // einen Link ist keine Bestätigung für einen Systemdialog.
        }
    }

    /**
     * Prüft, ob eine URL für den Browser-Aufruf erlaubt ist. Bewusst
     * paketsichtbar, damit die Sicherheitsregel testbar ist.
     */
    static boolean isSafeLinkUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String trimmed = url.trim();
        int colon = trimmed.indexOf(':');
        if (colon <= 0) {
            // kein Schema (relative Verweise) – nicht browserfähig
            return false;
        }
        String scheme = trimmed.substring(0, colon).toLowerCase(Locale.ROOT);
        return ALLOWED_LINK_SCHEMES.contains(scheme);
    }

    /**
     * Liefert das Rich-Text-Area-Control, das diese Fassade verwaltet.
     *
     * @return eingebettetes JavaFX-Control
     */
    public RichTextArea getControl() {
        return control;
    }

    // ---------- Textformatierung (Selektion → Decoration) ----------

    /** Fett ein/aus (wie Strg+B). */
    public void toggleBold() {
        toggleTextDecoration(
                td -> td.getFontWeight() == FontWeight.BOLD ? FontWeight.NORMAL : FontWeight.BOLD,
                (builder, value) -> builder.fontWeight(value).build());
    }

    /** Kursiv ein/aus (wie Strg+I). */
    public void toggleItalic() {
        toggleTextDecoration(
                td -> td.getFontPosture() == FontPosture.ITALIC ? FontPosture.REGULAR : FontPosture.ITALIC,
                (builder, value) -> builder.fontPosture(value).build());
    }

    /**
     * Unterstrichen ein/aus. Eigener Accelerator (Strg+U) – in rich-text-area
     * 1.2.6 sind nur Strg+B und Strg+I builtin (PROJEKTPLAN 3.3).
     */
    public void toggleUnderline() {
        toggleTextDecoration(
                td -> !Boolean.TRUE.equals(td.isUnderline()),
                (builder, value) -> builder.underline(value).build());
    }

    /** Durchgestrichen ein/aus. */
    public void toggleStrikethrough() {
        toggleTextDecoration(
                td -> !Boolean.TRUE.equals(td.isStrikethrough()),
                (builder, value) -> builder.strikethrough(value).build());
    }

    /**
     * Inline-Code ein/aus (monospaced ohne Hintergrund-Marker – Abgrenzung zum
     * Code-Block, PROJEKTPLAN 5.3).
     */
    public void toggleInlineCode() {
        String defaultFontFamily = TextDecoration.builder().presets().build().getFontFamily();
        toggleTextDecoration(
                td -> !MarkdownStyles.MONOSPACED.equals(td.getFontFamily()),
                (builder, value) -> builder.fontFamily(
                        value ? MarkdownStyles.MONOSPACED : defaultFontFamily).build());
    }

    /**
     * Link auf die aktuelle Selektion anwenden (PROJEKTPLAN 7.1).
     * Ohne Selektion wird die URL als decorationAtCaret gesetzt (Tipp-Modus).
     *
     * @param url Zieladresse, die der aktuellen Auswahl beziehungsweise
     *            zukünftiger Eingabe zugeordnet wird
     */
    public void applyLink(String url) {
        TextDecoration current = control.getDecorationAtCaret() instanceof TextDecoration td
                ? td
                : TextDecoration.builder().presets().build();
        TextDecoration.Builder builder = TextDecoration.builder().fromDecoration(current);
        decorate(builder.url(url).build());
    }

    // ---------- Absatzformate ----------

    /**
     * Überschrift der Ebene 1..6 anwenden (kanonisches Mapping über MarkdownStyles).
     *
     * @param level Überschriftenebene zwischen 1 und 6
     * @throws IllegalArgumentException wenn die Ebene ungültig ist
     */
    public void applyHeading(int level) {
        double size = MarkdownStyles.headingSize(level);
        if (size < 0) {
            throw new IllegalArgumentException("Invalid heading level: " + level);
        }
        decorate(
                ParagraphDecoration.builder().presets().build(),
                TextDecoration.builder().presets().fontSize(size).fontWeight(FontWeight.BOLD).build());
    }

    /** Absatzformat „Absatz" (Default) – setzt Überschriften/Listen/Zitate zurück. */
    public void applyParagraphStyle() {
        decorate(
                ParagraphDecoration.builder().presets().build(),
                TextDecoration.builder().presets().build());
    }

    /** Zitat anwenden (Einrückungs-Ebene 1, graphicType NONE). */
    public void applyQuote() {
        decorate(ParagraphDecoration.builder().presets().indentationLevel(1).build());
    }

    /** Aufzählungsliste ein/aus. */
    public void toggleBulletList() {
        toggleList(ParagraphDecoration.GraphicType.BULLETED_LIST);
    }

    /** Nummerierte Liste ein/aus. */
    public void toggleNumberedList() {
        toggleList(ParagraphDecoration.GraphicType.NUMBERED_LIST);
    }

    /** Einzug erhöhen (verschachtelte Listen, Zitate). */
    public void indent() {
        ParagraphDecoration current = control.getDecorationAtParagraph();
        int level = current == null ? 0 : current.getIndentationLevel();
        decorate(paragraphWithLevel(current, level + 1));
    }

    /** Einzug verringern. */
    public void outdent() {
        ParagraphDecoration current = control.getDecorationAtParagraph();
        int level = current == null ? 0 : current.getIndentationLevel();
        if (level <= 0) {
            return;
        }
        decorate(paragraphWithLevel(current, level - 1));
    }

    /**
     * Lädt Markdown-Text in den Editor (PROJEKTPLAN 5.2). Ersetzt den gesamten
     * Inhalt. Die Aktion läuft asynchron über die ActionFactory (wie alle
     * Editor-Operationen).
     *
     * @param markdown einzulesender Markdown-Quelltext
     */
    public void setMarkdown(String markdown) {
        com.gluonhq.richtextarea.model.Document document =
                new de.richmarkdown.markdown.MarkdownParser().parseInto(markdown);
        // Kein notifyChanged(): die Bibliothek lädt asynchron (verschachteltes
        // runLater) und meldet über documentProperty – das ist der spätere,
        // konsistente Zeitpunkt. Die Strukturansicht hört darauf.
        control.getActionFactory().open(document).execute(new ActionEvent());
    }

    // ---------- Einfüge-Operationen ----------

    /**
     * Fügt eine Horizontal Rule ein (PROJEKTPLAN 7.1). Abbildung im Document:
     * ein Absatz nur aus Leerzeichen, vollständig durchgestrichen – die einzige
     * darstellbare Form (die Bibliothek kennt keine Rahmen/Border).
     *
     * <p>Strategie: Die Striketrough-Dekoration wird als decorationAtCaret
     * gesetzt („Tipp-Modus"), sodass die eingefügten Leerzeichen sie erben.
     * Danach wird der Tipp-Modus zurückgesetzt, damit Folgeeingaben normal sind.
     */
    public void insertHorizontalRule() {
        int start = control.getCaretPosition();
        control.getActionFactory().insertText("\n" + HR_SPACES + "\n").execute(new ActionEvent());
        notifyChanged();
        // Nur die Leerzeichen dekoriertieren (die umgebenden Newlines nicht)
        control.getActionFactory().selectAndDecorate(
                new com.gluonhq.richtextarea.Selection(start + 1, start + 1 + HR_SPACES.length()),
                TextDecoration.builder().presets().strikethrough(true).build())
                .execute(new ActionEvent());
    }

    /**
     * Fügt ein Bild ein (PROJEKTPLAN 7.2). Die Bibliothek fügt über
     * {@code decorate(ImageDecoration)} ein ImageUnit an der Caret-Position ein.
     * Ein vorangestellter Absatzumbruch stellt sicher, dass das Bild ein
     * eigener Block ist (Markdown: {@code ![alt](url)} allein auf der Zeile).
     *
     * @param url Zieladresse des Bildes
     */
    public void insertImage(String url) {
        control.getActionFactory().insertText("\n").execute(new ActionEvent());
        decorate(new com.gluonhq.richtextarea.model.ImageDecoration(url));
    }

    /**
     * Fügt einen Code-Block ein (PROJEKTPLAN 7.2): eigener Absatz, monospaced
     * mit Hintergrund-Marker. Mehrzeilige Blöcke werden über den Tipp-Modus
     * komplett dekoriert (jede Zeile erbt die Dekoration).
     *
     * @param code Codeinhalt einschließlich möglicher Zeilenumbrüche
     */
    public void insertCodeBlock(String code) {
        int start = control.getCaretPosition();
        control.getActionFactory().insertText("\n" + code + "\n").execute(new ActionEvent());
        // Nur den Code-Inhalt dekorieren (die umgebenden Newlines nicht)
        control.getActionFactory().selectAndDecorate(
                new com.gluonhq.richtextarea.Selection(start + 1, start + 1 + code.length()),
                TextDecoration.builder().presets()
                        .fontFamily(MarkdownStyles.MONOSPACED)
                        .background(MarkdownStyles.CODE_BLOCK_BACKGROUND)
                        .build())
                .execute(new ActionEvent());
    }

    /**
     * Fügt eine Tabelle mit der angegebenen Zeilen- und Spaltenzahl ein
     * (PROJEKTPLAN 7.1). Nutzt die native Tabellen-Darstellung der Bibliothek:
     * Ein Absatz mit {@link TableDecoration} in der ParagraphDecoration, Zellen
     * getrennt durch {@code \u200b} (ZERO_WIDTH_TABLE_SEPARATOR) in
     * Zeilen-Hauptordnung. Die Tabelle wird damit wie in Word/LibreOffice
     * Writer als echte Tabelle bearbeitet (Zellen beschreiben, Zeilen/Spalten
     * über das Kontextmenü des Controls ergänzen); Markdown entsteht erst beim
     * Speichern (GFM-Tabelle über
     * {@link de.richmarkdown.markdown.MarkdownWriter}).
     *
     * @param rows Anzahl der Tabellenzeilen einschließlich Kopfzeile
     * @param columns Anzahl der Tabellenspalten
     */
    public void insertTable(int rows, int columns) {
        control.getActionFactory()
                .insertTable(new TableDecoration(rows, columns))
                .execute(new ActionEvent());
        notifyChanged();
    }

    /**
     * Erzeugt eine Druckfassung des Dokuments als eigenständiges Control
     * (PROJEKTPLAN 14): eigenes Control mit eigener Breite (der bedruckbaren
     * Seitenbreite) und eigener Höhe. Das angezeigte Dokument bleibt
     * unberührt – Zoom und Fenstergröße gehen nicht in den Ausdruck ein.
     *
     * <p>Zweiphasig: Das Control braucht zum Laden einen Skin, also eine
     * Szene. Nach dem Einbau in eine (angezeigte) Szene folgt
     * {@link #loadPrintCopy(RichTextArea)}; das Laden selbst läuft
     * asynchron (ein Puls der Bibliothek).
     *
     * @return neues, zunächst nicht geladenes Rich-Text-Area-Control
     */
    public RichTextArea createPrintCopy() {
        return new RichTextArea();
    }

    /**
     * Lädt das aktuelle Dokument in eine per {@link #createPrintCopy()}
     * erzeugte Druckfassung. Muss auf dem FX-Thread aufgerufen werden,
     * nachdem das Control in einer Szene liegt (Skin).
     *
     * @param copy zuvor erstelltes und in eine Szene eingebautes Druck-Control
     */
    public void loadPrintCopy(RichTextArea copy) {
        com.gluonhq.richtextarea.model.Document document =
                new de.richmarkdown.markdown.MarkdownParser().parseInto(getMarkdown());
        if (copy.getSkin() instanceof RichTextAreaSkin skin) {
            // synchron in den ViewModel der Kopie (nur open() ist package-private
            // erreichbar – wie save() in getMarkdown)
            new com.gluonhq.richtextarea.viewmodel.ActionCmdFactory()
                    .open(document).apply(skin.getViewModel());
        }
    }

    // ---------- Dokumentstruktur (Navigation) ----------

    /**
     * Ermittelt alle Überschriften des Dokuments (Ebene, Text, Caret-Position)
     * für die Dokumentstruktur-Anzeige (PROJEKTPLAN 7.1, Nachtrag).
     *
     * <p>Liest direkt aus dem ViewModel (ohne {@code save()}), damit die
     * Strukturanzeige den Gespeichert-Status nicht verfälscht.
     *
     * @return Überschriften in Dokumentreihenfolge; leer, solange kein
     *         Skin-ViewModel verfügbar ist oder keine Überschriften vorhanden sind
     */
    public java.util.List<DocumentHeading> getHeadings() {
        RichTextAreaViewModel viewModel = viewModel();
        if (viewModel == null) {
            return List.of();
        }
        List<DocumentHeading> headings = new ArrayList<>();
        List<com.gluonhq.richtextarea.model.Paragraph> paragraphs = viewModel.getParagraphList();
        List<DecorationModel> models =
                viewModel.getTextBuffer().getDecorationModelList(0, viewModel.getTextLength());

        // Dekorationsmodelle und Absätze in einem Durchgang verschneiden
        int modelIndex = 0;
        for (com.gluonhq.richtextarea.model.Paragraph paragraph : paragraphs) {
            while (modelIndex < models.size()
                    && models.get(modelIndex).getStart() + models.get(modelIndex).getLength()
                            <= paragraph.getStart()) {
                modelIndex++;
            }
            if (modelIndex >= models.size()) {
                break;
            }
            DecorationModel model = models.get(modelIndex);
            if (model.getDecoration() instanceof TextDecoration td) {
                int level = MarkdownStyles.headingLevel(td.getFontSize());
                if (level > 0 && td.getFontWeight() == FontWeight.BOLD) {
                    String text = viewModel.getTextBuffer()
                            .getText(paragraph.getStart(), paragraph.getEnd())
                            .replace("\n", " ")
                            .trim();
                    headings.add(new DocumentHeading(level, text, paragraph.getStart()));
                }
            }
        }
        return headings;
    }

    /**
     * Setzt die Caret-Position (Navigation über die Dokumentstruktur) und macht
     * die Stelle sichtbar (PROJEKTPLAN 7.1, Nachtrag).
     *
     * <p>Zwei Punkte muss die Fassade selbst erledigen – die Skin reicht dafür
     * nicht aus:
     * <ul>
     *   <li><b>Scrollen:</b> Die Skin scrollt nur, wenn der Caret bereits
     *   **gerendert** ist. Nach einem Klick in die Strukturliste ist genau das
     *   nicht der Fall (die Zelle des Ziels liegt außerhalb des Viewports, die
     *   alte Caret-Form ist ohne Fokus unsichtbar) – der Caret würde zwar
     *   springen, das Dokument bliebe aber an der alten Stelle stehen; für den
     *   Anwender sieht das aus wie „keine Reaktion".</li>
     *   <li><b>Fokus:</b> Der Klick geht an die Strukturliste – der Caret im
     *   Dokument wäre unsichtbar. Wie in Word/LibreOffice wandert der Fokus
     *   zurück ins Dokument. Die Anforderung läuft bewusst verzögert
     *   ({@link Platform#runLater}): Bei einem Mausklick vergibt die Scene den
     *   Fokus erst nach Auslieferung des Ereignisses an das geklickte Element –
     *   ein synchrones {@code requestFocus()} aus dem Selektionen-Listener der
     *   Liste würde davon wieder überschrieben.</li>
     * </ul>
     *
     * @param position nullbasierte Textposition; Werte außerhalb des
     *                 Dokumentbereichs werden auf die gültige Spanne begrenzt
     */
    public void goToPosition(int position) {
        RichTextAreaViewModel viewModel = viewModel();
        if (viewModel == null) {
            return;
        }
        int target = Math.max(0, Math.min(position, viewModel.getTextLength()));
        viewModel.setCaretPosition(target);
        scrollToParagraphWithPosition(target);
        Platform.runLater(() -> {
            if (control.getScene() != null) {
                control.requestFocus();
            }
        });
    }

    /**
     * Scrollt den Absatz, der die gegebene Caret-Position enthält, in den
     * Sichtbereich (Ansprung oben – wie in Word/LibreOffice). Die Absatz-Liste
     * des Skins ist über ihre Style-Class erreichbar (wie in der
     * Zeilennummern-Anzeige, PROJEKTPLAN 10).
     */
    private void scrollToParagraphWithPosition(int position) {
        RichTextAreaViewModel viewModel = viewModel();
        ListView<com.gluonhq.richtextarea.model.Paragraph> listView = paragraphListView();
        if (viewModel == null || listView == null) {
            return;
        }
        int index = indexOfParagraphWithPosition(viewModel.getParagraphList(), position);
        if (index >= 0 && index < listView.getItems().size()) {
            listView.scrollTo(index);
        }
    }

    /**
     * Index des Absatzes, der die Position enthält (Absätze sind nach Start
     * sortiert; der letzte Absatz mit {@code start <= position} enthält sie).
     */
    private static int indexOfParagraphWithPosition(
            java.util.List<com.gluonhq.richtextarea.model.Paragraph> paragraphs, int position) {
        int index = -1;
        for (int i = 0; i < paragraphs.size(); i++) {
            if (paragraphs.get(i).getStart() <= position) {
                index = i;
            } else {
                break;
            }
        }
        return index;
    }

    /** Absatz-Liste des Editor-Skins (Style-Class der Bibliothek). */
    @SuppressWarnings("unchecked")
    private ListView<com.gluonhq.richtextarea.model.Paragraph> paragraphListView() {
        return control.lookup(".paragraph-list-view") instanceof ListView<?> listView
                ? (ListView<com.gluonhq.richtextarea.model.Paragraph>) listView
                : null;
    }

    /**
     * Meldet eine Dokumentänderung (nach erfolgter Ausführung der Aktion).
     * Wird von der Dokumentstruktur-Anzeige genutzt.
     *
     * @param listener Callback, der nach einer Dokumentänderung auf dem
     *                 JavaFX Application Thread ausgeführt wird
     */
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    /**
     * Entfernt einen zuvor registrierten Änderungslistener.
     *
     * @param listener zu entfernender Listener
     */
    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    private void notifyChanged() {
        if (changeListeners.isEmpty()) {
            return;
        }
        Platform.runLater(() -> {
            for (Runnable listener : changeListeners) {
                listener.run();
            }
        });
    }

    // ---------- Properties für Statusleiste/Toolbar ----------

    /**
     * Serialisiert den aktuellen Dokumentzustand als Markdown
     * (PROJEKTPLAN 5.2). Muss auf dem FX-Thread aufgerufen werden.
     *
     * <p>Hinweis: Die Bibliothek aktualisiert die {@code documentProperty} nur
     * über {@code save()} (oder autoSave). {@code save()} setzt dabei auch den
     * Gespeichert-Status – genau das gewünschte Verhalten beim Datei-Speichern.
     *
     * @return serialisierter Markdown-Quelltext
     */
    public String getMarkdown() {
        RichTextAreaViewModel viewModel = viewModel();
        if (viewModel == null) {
            return "";
        }
        // synchroner Save über die ActionCmdFactory (viewModel.save() ist package-private)
        new com.gluonhq.richtextarea.viewmodel.ActionCmdFactory().save().apply(viewModel);
        return new de.richmarkdown.markdown.MarkdownWriter().write(viewModel.getDocument());
    }

    /**
     * Liefert die lesbare Property für den Änderungsstatus.
     *
     * @return Property, deren Wert bei ungespeicherten Änderungen {@code true} ist
     */
    public ReadOnlyBooleanProperty modifiedProperty() {
        return control.modifiedProperty();
    }

    /**
     * Gibt an, ob ungespeicherte Änderungen vorliegen.
     *
     * @return {@code true}, wenn das Dokument geändert wurde
     */
    public boolean isModified() {
        return control.isModified();
    }

    /**
     * Liefert die lesbare Property der aktuellen Caret-Position.
     *
     * @return nullbasierte Zeichenposition im Dokument
     */
    public ReadOnlyIntegerProperty caretPositionProperty() {
        return control.caretPositionProperty();
    }

    /**
     * Liefert die lesbare Property der aktuellen Dokumenttextlänge.
     *
     * @return Zeichenanzahl des Dokumentinhalts
     */
    public ReadOnlyIntegerProperty textLengthProperty() {
        return control.textLengthProperty();
    }

    // ---------- Interna ----------

    private void decorate(com.gluonhq.richtextarea.model.Decoration... decorations) {
        control.getActionFactory().decorate(decorations).execute(new ActionEvent());
        notifyChanged();
    }

    /**
     * Toggle-Logik für TextDecoration: Der Zielzustand wird aus der aktuellen
     * Caret-Dekoration abgeleitet (Word: erneuter Klick entfernt die Formatierung).
     * Ohne Selektion setzt die Bibliothek das Decoration als decorationAtCaret
     * (Eingabemodus) – genau wie Word.
     */
    private <T> void toggleTextDecoration(Function<TextDecoration, T> nextValue,
                                          BiFunction<TextDecoration.Builder, T, TextDecoration> builderFunction) {
        TextDecoration current = control.getDecorationAtCaret() instanceof TextDecoration td
                ? td
                : TextDecoration.builder().presets().build();
        T value = nextValue.apply(current);
        TextDecoration.Builder builder = TextDecoration.builder().fromDecoration(current);
        decorate(builderFunction.apply(builder, value));
    }

    private ParagraphDecoration paragraphWithLevel(ParagraphDecoration current, int level) {
        ParagraphDecoration.Builder builder = ParagraphDecoration.builder().presets();
        if (current != null && current.getGraphicType() != ParagraphDecoration.GraphicType.NONE) {
            builder.graphicType(current.getGraphicType());
        }
        return builder.indentationLevel(level).build();
    }

    private void toggleList(ParagraphDecoration.GraphicType graphicType) {
        ParagraphDecoration current = control.getDecorationAtParagraph();
        boolean active = current != null && current.getGraphicType() == graphicType;
        ParagraphDecoration.Builder builder = ParagraphDecoration.builder().presets();
        if (!active) {
            builder.graphicType(graphicType);
        }
        decorate(builder.build());
    }

    /**
     * Installiert das vollständige Enter-Regelwerk (PROJEKTPLAN 4.5) über den
     * {@code onAction}-Hook: Die Skin ruft bei gesetztem onAction (und nicht
     * Shift) unseren Handler statt {@code insertText("\n")} auf.
     *
     * <p>Reihenfolge (verbindlich):
     * <ol>
     *   <li>Leerer Absatz mit Überschriften-Dekoration → Stil „Absatz", kein Newline</li>
     *   <li>Nicht leerer Absatz mit Überschriften-Dekoration → Newline, dann Stil „Absatz" auf den neuen Absatz</li>
     *   <li>Leerer Absatz mit Zitat-Einzug (indentationLevel &gt; 0, kein graphicType) → Einzug 0, kein Newline</li>
     *   <li>Leerer Absatz mit Listendekoration → Builtin-Dedent replizieren (Ebene 0: graphicType entfernt; Ebene ≥ 1: eine Ebene ausrücken), kein Newline</li>
     *   <li>Alle anderen Fälle → {@code insertText("\n")} (Listen-/Zitat-Fortsetzung builtin)</li>
     * </ol>
     */
    private void installEnterBehavior() {
        control.setOnAction(event -> handleEnter());
    }

    private void handleEnter() {
        ParagraphDecoration decoration = control.getDecorationAtParagraph();
        boolean emptyParagraph = isEmptyParagraphAtCaret();
        boolean heading = isHeadingAtCaret();

        if (decoration != null && emptyParagraph && heading) {
            // 1. Leere Überschrift → zurück zu "Absatz", Absatz bleibt stehen
            resetToParagraphStyle();
            return;
        }

        if (decoration != null && !emptyParagraph && heading) {
            // 2. Nicht leere Überschrift → neue Zeile ist "Absatz"
            control.getActionFactory().insertText("\n").execute(new ActionEvent());
            resetToParagraphStyle();
            return;
        }

        if (decoration != null && emptyParagraph
                && decoration.getGraphicType() == ParagraphDecoration.GraphicType.NONE
                && decoration.getIndentationLevel() > 0) {
            // 3. Leerer Zitat-Absatz → Zitat endet
            decorate(ParagraphDecoration.builder().presets().build());
            return;
        }

        if (decoration != null && emptyParagraph
                && decoration.getGraphicType() != ParagraphDecoration.GraphicType.NONE) {
            // 4. Leerer Listeneintrag: Builtin-Dedent replizieren (der onAction-Hook
            //    übersteuert die Builtin-Behandlung der Skin vollständig).
            //    Ebene 0 → graphicType entfernt; Ebene >= 1 → eine Ebene ausrücken.
            //    (Hinweis: graphicType(!=NONE) erzwingt Level >= 1 im Builder,
            //    daher endet die Liste bei Ebene 1 über presets.)
            int level = decoration.getIndentationLevel();
            if (level <= 0) {
                decorate(ParagraphDecoration.builder().presets().build());
            } else {
                decorate(ParagraphDecoration.builder().presets()
                        .graphicType(decoration.getGraphicType())
                        .indentationLevel(level - 1)
                        .build());
            }
            return;
        }

        // 5. Alle anderen Fälle: normaler Absatzumbruch (Listen-/Zitat-Fortsetzung builtin)
        control.getActionFactory().insertText("\n").execute(new ActionEvent());
    }

    /**
     * Setzt Absatz- UND Textdekoration auf „Absatz" (Default). Beides ist nötig:
     * die Bibliothek verwendet die decorationAtCaret („Tipp-Modus") für neu
     * eingefügten Text – würde nur die ParagraphDecoration zurückgesetzt,
     * produzierte weiteres Tippen noch Überschrift-Text.
     */
    private void resetToParagraphStyle() {
        decorate(
                ParagraphDecoration.builder().presets().build(),
                TextDecoration.builder().presets().build());
    }

    /**
     * Überschriften-Erkennung an der Caret-Position über die TextDecoration:
     * fontSize ist eindeutiger Heading-Marker (MarkdownStyles), bold bestätigt.
     * Body-Text (14px) ist bewusst keine Heading-Größe.
     */
    private boolean isHeadingAtCaret() {
        if (control.getDecorationAtCaret() instanceof TextDecoration td) {
            return MarkdownStyles.isHeadingSize(td.getFontSize())
                    && td.getFontWeight() == FontWeight.BOLD;
        }
        return false;
    }

    /**
     * Prüft über den ViewModel der Skin, ob der Absatz an der Caret-Position
     * leer ist (nur das Absatzzeichen bzw. null Länge).
     */
    private boolean isEmptyParagraphAtCaret() {
        RichTextAreaViewModel viewModel = viewModel();
        if (viewModel == null) {
            return false;
        }
        return viewModel.getParagraphWithCaret()
                .map(viewModel::isEmptyParagraph)
                .orElse(true);
    }

    private RichTextAreaViewModel viewModel() {
        if (control.getSkin() instanceof RichTextAreaSkin skin) {
            return skin.getViewModel();
        }
        return null;
    }

    /**
     * Eigener Strg+U-Accelerator (in 1.2.6 nicht builtin). Wird auf dem Control
     * als KEY_PRESSED-Filter registriert und konsumiert, damit die Skin ihn
     * nicht anderweitig verarbeitet.
     */
    private void installUnderlineAccelerator() {
        KeyCombination underline = new KeyCodeCombination(KeyCode.U, KeyCombination.SHORTCUT_DOWN);
        control.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (underline.match(event)) {
                toggleUnderline();
                event.consume();
            }
        });
    }
}
