package de.richmarkdown.findreplace;

import com.gluonhq.richtextarea.RichTextArea;
import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.Selection;
import com.gluonhq.richtextarea.model.Decoration;
import com.gluonhq.richtextarea.model.DecorationModel;
import com.gluonhq.richtextarea.model.Document;
import com.gluonhq.richtextarea.model.Paragraph;
import com.gluonhq.richtextarea.model.ParagraphDecoration;
import com.gluonhq.richtextarea.model.TextBuffer;
import com.gluonhq.richtextarea.model.TextDecoration;
import com.gluonhq.richtextarea.model.TextUnit;
import com.gluonhq.richtextarea.model.Unit;
import com.gluonhq.richtextarea.viewmodel.RichTextAreaViewModel;
import javafx.event.ActionEvent;
import javafx.scene.control.ListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Such- und Ersetzungslogik nach LibreOffice Writer
 * „Bearbeiten → Suchen &amp; Ersetzen“ (AddFeatures #11).
 * Nutzt nur oeffentliche RTA-APIs: Lesen via TextBuffer, Markieren via
 * ViewModel, Ersetzen via ActionFactory (undo-faehig).
 */
public class FindReplaceEngine {

    /** Ergebnis der Wrap-Pruefung fuer Weitersuchen/Ersetzen. */
    public enum Wrap {
        FOUND,
        WRAPPED,
        NOT_FOUND
    }

    /** Suchoptionen analog zum LO-Dialog. */
    public static class FindOptions {
        private boolean matchCase;
        private boolean wholeWords;
        private boolean regex;
        private boolean wildcards;
        private boolean backwards;
        private boolean selectionOnly;
        private boolean strikethroughOnly;

        public boolean isMatchCase() { return matchCase; }
        public void setMatchCase(boolean v) { matchCase = v; }
        public boolean isWholeWords() { return wholeWords; }
        public void setWholeWords(boolean v) { wholeWords = v; }
        public boolean isRegex() { return regex; }
        public void setRegex(boolean v) { regex = v; }
        public boolean isWildcards() { return wildcards; }
        public void setWildcards(boolean v) { wildcards = v; }
        public boolean isBackwards() { return backwards; }
        public void setBackwards(boolean v) { backwards = v; }
        public boolean isSelectionOnly() { return selectionOnly; }
        public void setSelectionOnly(boolean v) { selectionOnly = v; }
        /** Nur durchgestrichenen Text treffen (Format-Suche analog LO). */
        public boolean isStrikethroughOnly() { return strikethroughOnly; }
        public void setStrikethroughOnly(boolean v) { strikethroughOnly = v; }

        public FindOptions copy() {
            FindOptions c = new FindOptions();
            c.matchCase = matchCase;
            c.wholeWords = wholeWords;
            c.regex = regex;
            c.wildcards = wildcards;
            c.backwards = backwards;
            c.selectionOnly = selectionOnly;
            c.strikethroughOnly = strikethroughOnly;
            return c;
        }
    }

    /** Treffer mit Dokument-Offsets (Start inklusiv, Ende exklusiv). */
    public static class Match {
        private final int start;
        private final int end;

        public Match(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public int getStart() { return start; }
        public int getEnd() { return end; }
        public int length() { return end - start; }

        @Override
        public String toString() { return "Match[" + start + "," + end + "]"; }
    }

    /** Suchergebnis aus Treffer und Wrap-Kennzeichen. */
    public static class SearchResult {
        private final Match match;
        private final Wrap wrap;

        public SearchResult(Match match, Wrap wrap) {
            this.match = match;
            this.wrap = wrap;
        }

        public Match getMatch() { return match; }
        public Wrap getWrap() { return wrap; }
    }

    private final RichTextArea control;
    private final RichTextAreaViewModel viewModel;
    /**
     * Scroll-Callback (Treffer-Ende als Editor-Position): wird nach jeder
     * Treffer-Markierung aufgerufen, damit der Treffer auch dann sichtbar
     * ist, wenn er ausserhalb des Viewports liegt. Default: no-op, damit
     * reine Logik-Tests ohne UI-Scroll laufen.
     */
    private java.util.function.IntConsumer revealHandler = position -> { };
    /**
     * Gemerkte Auswahl-Grenzen fuer „Nur Auswahl": bleibt ueber mehrere
     * Weitersuchen-Schritte stabil (die Treffer-Selektion wuerde den Scope
     * sonst auf den Treffer schrumpfen lassen, LO sucht weiter im
     * urspruenglichen Bereich). Wird beim naechsten findAll/findNext/
     * replaceAll mit selectionOnly gesetzt, wenn keine gueltige Ankerung
     * vorliegt oder die Auswahl ausserhalb liegt.
     */
    private int selectionAnchorStart = -1;
    private int selectionAnchorEnd = -1;
    /** Anzahl erfolgreich ersetzter Treffer beim letzten replaceAll. */
    private int lastReplacedCount = 0;
    /** Anzahl beim letzten replaceAll abgehnter Treffer (Absatzgrenze/Tabelle). */
    private int lastSkippedCount = 0;

    public FindReplaceEngine(RichTextArea control, RichTextAreaViewModel viewModel) {
        this.control = Objects.requireNonNull(control);
        this.viewModel = Objects.requireNonNull(viewModel);
    }

    public FindReplaceEngine(RichTextArea control) {
        this(control, ((RichTextAreaSkin) control.getSkin()).getViewModel());
    }

    /**
     * Setzt den Scroll-Callback fuer Treffer-Markierungen
     * (z. B. {@code editor::revealPosition}). {@code null} setzt auf no-op zurueck.
     */
    public void setRevealHandler(java.util.function.IntConsumer revealHandler) {
        this.revealHandler = revealHandler == null ? position -> { } : revealHandler;
    }

    public String getText() {
        return viewModel.getTextBuffer().getText();
    }

    public int getCaretPosition() {
        return Math.max(0, control.getCaretPosition());
    }

    private int toTextPosition(int editorPosition) {
        TextBuffer textBuffer = viewModel.getTextBuffer();
        int position = Math.max(0, Math.min(editorPosition, textBuffer.getTextLength()));
        return textBuffer.getText(0, position).length();
    }

    private Selection toEditorSelection(int textStart, int textEnd) {
        String text = getText();
        int start = Math.max(0, Math.min(textStart, text.length()));
        int end = Math.max(start, Math.min(textEnd, text.length()));
        return new Selection(toEditorPosition(start, false, text.length()),
                toEditorPosition(end, true, text.length()));
    }

    private int toEditorPosition(int textPosition, boolean selectionEnd, int totalTextLength) {
        TextBuffer textBuffer = viewModel.getTextBuffer();
        int target = Math.max(0, Math.min(textPosition, totalTextLength));
        int[] currentTextPosition = {0};
        int[] currentEditorPosition = {0};
        int[] editorPosition = {-1};
        textBuffer.walkFragments((Unit unit, Decoration decoration) -> {
            if (editorPosition[0] >= 0) {
                return;
            }
            String unitText = unit.getText();
            int textLength = unitText.length();
            if (target <= currentTextPosition[0] + textLength) {
                int offset = target - currentTextPosition[0];
                if (unit instanceof TextUnit) {
                    editorPosition[0] = currentEditorPosition[0] + offset;
                } else if (offset == 0) {
                    editorPosition[0] = currentEditorPosition[0];
                } else if (offset == textLength || selectionEnd) {
                    editorPosition[0] = currentEditorPosition[0] + unit.length();
                } else {
                    // Serialized image/emoji text can be wider than its editor position.
                    editorPosition[0] = currentEditorPosition[0];
                }
            } else {
                currentTextPosition[0] += textLength;
                currentEditorPosition[0] += unit.length();
            }
        }, 0, textBuffer.getTextLength());
        return editorPosition[0] >= 0 ? editorPosition[0] : textBuffer.getTextLength();
    }

    public Selection getSelection() {
        Selection selection = control.getSelection();
        return selection != null && selection.isDefined() ? selection : null;
    }
    public Pattern compilePattern(String searchText, FindOptions options) {
        if (searchText == null || searchText.isEmpty()) {
            return null;
        }
        FindOptions effective = options == null ? new FindOptions() : options;
        try {
            String regex = toRegex(searchText, effective);
            int flags = effective.isMatchCase() ? 0
                    : (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            return Pattern.compile(regex, flags);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    private String toRegex(String searchText, FindOptions options) {
        String body;
        if (options.isRegex()) {
            body = searchText;
        } else if (options.isWildcards()) {
            body = wildcardsToRegex(searchText);
        } else {
            body = Pattern.quote(searchText);
        }
        return body;
    }

    /**
     * Wortgrenzen-Pruefung als Nachfilter statt Regex-Wrapper: Loest das
     * Ersatzproblem bei {@code wholeWords + wildcards/regex} (dort schlug
     * {@code Matcher.find()} auf dem reinen Treffertext wegen fehlender
     * \\b-Kontexte fehl) und behandelt Umlaute/Unicode korrekt – <br>
     * \\b in Java haelt Umlaute fälschlich fuer Nicht-Wortzeichen.
     */
    private boolean isWordBoundary(String text, int index) {
        if (index <= 0 || index >= text.length()) {
            return true;
        }
        char before = text.charAt(index - 1);
        char after = text.charAt(index);
        boolean beforeWord = Character.isLetterOrDigit(before) || before == '_';
        boolean afterWord = Character.isLetterOrDigit(after) || after == '_';
        return beforeWord != afterWord;
    }

    private boolean respectsWholeWords(String text, int start, int end, FindOptions options) {
        if (options == null || !options.isWholeWords()) {
            return true;
        }
        return isWordBoundary(text, start) && isWordBoundary(text, end);
    }

    static String wildcardsToRegex(String searchText) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < searchText.length(); i++) {
            char c = searchText.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                case '\\', '(', ')', '[', ']', '{', '}', '.', '+', '^', '$', '|', '/' ->
                        sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
    private int[] scope(String fullText, FindOptions options) {
        if (options != null && options.isSelectionOnly()) {
            Selection selection = getSelection();
            int len = fullText == null ? 0 : fullText.length();
            int selStart = selection == null ? -1 : Math.max(0, Math.min(toTextPosition(selection.getStart()), len));
            int selEnd = selection == null ? -1 : Math.max(0, Math.min(toTextPosition(selection.getEnd()), len));
            boolean anchorValid = selectionAnchorStart >= 0 && selectionAnchorEnd > selectionAnchorStart
                    && selectionAnchorStart <= len && selectionAnchorEnd <= len;
            boolean selectionCoversAnchor = anchorValid && selection != null
                    && selStart <= selectionAnchorStart && selEnd >= selectionAnchorEnd;
            boolean selectionInsideAnchor = anchorValid && selection != null
                    && selStart >= selectionAnchorStart && selEnd <= selectionAnchorEnd;
            if (!anchorValid || (!selectionCoversAnchor && !selectionInsideAnchor)) {
                // Neue Ankerung: gueltige Auswahl merken, sonst kein Scope.
                if (selection == null || selEnd <= selStart) {
                    selectionAnchorStart = -1;
                    selectionAnchorEnd = -1;
                    return null;
                }
                selectionAnchorStart = selStart;
                selectionAnchorEnd = selEnd;
            }
            int start = Math.max(0, Math.min(selectionAnchorStart, len));
            int end = Math.max(0, Math.min(selectionAnchorEnd, len));
            if (end <= start) {
                return null;
            }
            return new int[]{start, end};
        }
        // Ohne Auswahl-Scope ist keine Ankerung aktiv.
        selectionAnchorStart = -1;
        selectionAnchorEnd = -1;
        return new int[]{0, fullText == null ? 0 : fullText.length()};
    }

    /**
     * Setzt den Auswahl-Scope explizit (z. B. aus dem Dialog beim Aktivieren
     * von „Nur Auswahl"), damit die erste Weitersuche im urspruenglichen
     * Bereich startet statt in der Treffer-Selektion.
     */
    public void lockSelectionScope(int start, int end, int textLength) {
        int maxPosition = Math.min(Math.max(0, textLength), viewModel.getTextBuffer().getTextLength());
        int s = toTextPosition(Math.max(0, Math.min(start, maxPosition)));
        int e = toTextPosition(Math.max(0, Math.min(end, maxPosition)));
        if (e <= s) {
            selectionAnchorStart = -1;
            selectionAnchorEnd = -1;
        } else {
            selectionAnchorStart = s;
            selectionAnchorEnd = e;
        }
    }

    /** Gibt die gemerkte Auswahl-Ankerung zurueck oder {@code null}. */
    public int[] selectionAnchor() {
        if (selectionAnchorStart >= 0 && selectionAnchorEnd > selectionAnchorStart) {
            return new int[]{selectionAnchorStart, selectionAnchorEnd};
        }
        return null;
    }

    /** Anzahl erfolgreich ersetzter Treffer beim letzten replaceAll. */
    public int getLastReplacedCount() {
        return lastReplacedCount;
    }

    /** Anzahl beim letzten replaceAll abgehnter Treffer. */
    public int getLastSkippedCount() {
        return lastSkippedCount;
    }

    public List<Match> findAll(String searchText, FindOptions options) {
        List<Match> matches = new ArrayList<>();
        Pattern pattern = compilePattern(searchText, options);
        if (pattern == null) {
            return matches;
        }
        FindOptions effective = options == null ? new FindOptions() : options;
        String fullText = getText();
        int[] range = scope(fullText, effective);
        if (range == null) {
            return matches;
        }
        Matcher matcher = pattern.matcher(fullText);
        matcher.region(range[0], range[1]);
        while (matcher.find()) {
            if (matcher.end() == matcher.start()) {
                if (matcher.end() >= range[1]) {
                    break;
                }
                matcher.region(matcher.end() + 1, range[1]);
                continue;
            }
            Match candidate = new Match(matcher.start(), matcher.end());
            if (effective.isStrikethroughOnly() && !isFullyStrikethrough(candidate)) {
                continue;
            }
            if (!respectsWholeWords(fullText, matcher.start(), matcher.end(), effective)) {
                continue;
            }
            matches.add(candidate);
        }
        return matches;
    }

    public SearchResult findNext(String searchText, FindOptions options, int startFrom) {
        FindOptions effective = options == null ? new FindOptions() : options;
        Pattern pattern = compilePattern(searchText, effective);
        if (pattern == null) {
            return null;
        }
        String fullText = getText();
        int[] range = scope(fullText, effective);
        if (range == null) {
            return null;
        }
        int scopeStart = range[0];
        int scopeEnd = range[1];
        int start = Math.max(scopeStart, Math.min(startFrom, scopeEnd));
        if (effective.isBackwards()) {
            // Rueckwaerts: Treffer endet vor dem Start (Anfang waehlt exklusiv).
            Match before = lastMatchEndingBefore(pattern, fullText, scopeStart, scopeEnd, start, effective);
            if (before != null) {
                return new SearchResult(before, Wrap.FOUND);
            }
            Match wrapped = lastMatchBefore(pattern, fullText, scopeStart, scopeEnd, effective);
            if (wrapped != null) {
                return new SearchResult(wrapped, Wrap.WRAPPED);
            }
            return null;
        }
        Match next = firstMatchFrom(pattern, fullText, start, scopeEnd, effective);
        if (next != null) {
            return new SearchResult(next, Wrap.FOUND);
        }
        Match wrapped = firstMatchFrom(pattern, fullText, scopeStart, start, effective);
        if (wrapped != null) {
            return new SearchResult(wrapped, Wrap.WRAPPED);
        }
        return null;
    }

    public SearchResult findNext(String searchText, FindOptions options) {
        return findNext(searchText, options, caretForSearch(options));
    }

    private int caretForSearch(FindOptions options) {
        Selection selection = getSelection();
        if (selection == null || options == null) {
            return toTextPosition(getCaretPosition());
        }
        if (options.isBackwards()) {
            return toTextPosition(selection.getStart());
        }
        return toTextPosition(selection.getEnd());
    }

    private Match firstMatchFrom(Pattern pattern, String text, int from, int to, FindOptions options) {
        if (from >= to) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        matcher.region(from, to);
        while (matcher.find()) {
            if (matcher.end() == matcher.start()) {
                if (matcher.end() >= to) {
                    return null;
                }
                matcher.region(matcher.end() + 1, to);
                continue;
            }
            Match candidate = new Match(matcher.start(), matcher.end());
            if (options != null && options.isStrikethroughOnly() && !isFullyStrikethrough(candidate)) {
                continue;
            }
            if (!respectsWholeWords(text, matcher.start(), matcher.end(), options)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private Match firstMatchFrom(Pattern pattern, String text, int from, int to) {
        return firstMatchFrom(pattern, text, from, to, null);
    }

    private Match lastMatchEndingBefore(Pattern pattern, String text, int from, int to, int start,
            FindOptions options) {
        if (from >= to || start <= from) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        matcher.region(from, to);
        Match last = null;
        while (matcher.find()) {
            if (matcher.end() == matcher.start()) {
                if (matcher.end() >= to) {
                    break;
                }
                matcher.region(matcher.end() + 1, to);
                continue;
            }
            if (matcher.end() <= start) {
                Match candidate = new Match(matcher.start(), matcher.end());
                if (options != null && options.isStrikethroughOnly() && !isFullyStrikethrough(candidate)) {
                    continue;
                }
                if (!respectsWholeWords(text, matcher.start(), matcher.end(), options)) {
                    continue;
                }
                last = candidate;
            } else {
                break;
            }
        }
        return last;
    }

    private Match lastMatchBefore(Pattern pattern, String text, int from, int to, FindOptions options) {
        if (from >= to) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        matcher.region(from, to);
        Match last = null;
        while (matcher.find()) {
            if (matcher.end() == matcher.start()) {
                if (matcher.end() >= to) {
                    break;
                }
                matcher.region(matcher.end() + 1, to);
                continue;
            }
            Match candidate = new Match(matcher.start(), matcher.end());
            if (options != null && options.isStrikethroughOnly() && !isFullyStrikethrough(candidate)) {
                continue;
            }
            if (!respectsWholeWords(text, matcher.start(), matcher.end(), options)) {
                continue;
            }
            last = candidate;
        }
        return last;
    }

    private Match lastMatchEndingBefore(Pattern pattern, String text, int from, int to, int start) {
        return lastMatchEndingBefore(pattern, text, from, to, start, null);
    }

    private Match lastMatchBefore(Pattern pattern, String text, int from, int to) {
        return lastMatchBefore(pattern, text, from, to, null);
    }

    public SearchResult replaceSelection(String searchText, String replaceText, FindOptions options) {
        FindOptions effective = options == null ? new FindOptions() : options;

        Selection selection = getSelection();
        String replacement = replaceText == null ? "" : replaceText;
        if (selection == null) {
            // Keine Auswahl: nur zum naechsten Treffer springen, nicht ersetzen.
            return findNext(searchText, effective);
        }
        boolean replaced = false;
        if (selection != null) {
            Pattern pattern = compilePattern(searchText, effective);
            String fullText = getText();
            int start = Math.max(0, Math.min(toTextPosition(selection.getStart()), fullText.length()));
            int end = Math.max(0, Math.min(toTextPosition(selection.getEnd()), fullText.length()));
            if (pattern != null && end > start) {
                String selected = fullText.substring(start, end);
                boolean matches = selectionMatches(pattern, searchText, selected, effective);
                if (matches) {
                    String resolved = resolveReplacement(searchText, replacement, effective, selected);
                    if (replaceMatchSafely(new Match(start, end), resolved)) {
                        replaced = true;
                        // Caret ans Ende des Ersatztexts setzen, damit die Folgesuche
                        // hinter dem Ersatz weiterlaeuft (LO-Verhalten).
                        viewModel.setCaretPosition(toEditorPosition(start + resolved.length(), true, getText().length()));
                        viewModel.clearSelection();
                    }
                }
            }
        }
        if (!replaced) {
            // Ersatz wurde abgelehnt (Absatzgrenze/Tabelle) – nichts ersetzt.
            return null;
        }
        SearchResult next = findNext(searchText, effective);
        if (next != null) {
            return next;
        }
        // Ersatz erfolgreich, aber kein weiterer Treffer: Aufrufer ueber
        // NOT_FOUND informieren (nicht null), damit keine irrefuehrende
        // „Keine Uebereinstimmung“-Meldung erscheint.
        int caret = toTextPosition(getCaretPosition());
        return new SearchResult(new Match(caret, caret), Wrap.NOT_FOUND);
    }

    private boolean selectionMatches(Pattern pattern, String searchText, String selected, FindOptions options) {
        if (options.isRegex() || options.isWildcards() || options.isWholeWords()) {
            return pattern.matcher(selected).matches();
        }
        if (options.isMatchCase()) {
            return selected.equals(searchText);
        }
        return selected.equalsIgnoreCase(searchText);
    }

    /**
     * Liefert die {@link TextDecoration}, die die Textposition {@code position}
     * bedeckt, oder {@code null}, wenn keine spezifische Textformatierung
     * vorliegt (dann gilt der Body-Standard).
     */
    private TextDecoration captureTextDecoration(int position) {
        Document document = viewModel.getDocument();
        if (document == null || document.getDecorations() == null) {
            return null;
        }
        for (DecorationModel model : document.getDecorations()) {
            if (model == null) {
                continue;
            }
            int fragStart = model.getStart();
            int fragEnd = fragStart + model.getLength();
            if (position >= fragStart && position < fragEnd && model.getDecoration() instanceof TextDecoration td) {
                return td;
            }
        }
        return null;
    }

    /**
     * Prueft, ob die Position in einem Absatz mit {@link TableDecoration}
     * liegt.
     */
    private boolean isParagraphWithTable(int position) {
        Document document = viewModel.getDocument();
        if (document == null || document.getDecorations() == null) {
            return false;
        }
        for (DecorationModel model : document.getDecorations()) {
            if (model == null) {
                continue;
            }
            int fragStart = model.getStart();
            int fragEnd = fragStart + model.getLength();
            if (position >= fragStart && position < fragEnd) {
                ParagraphDecoration pd = model.getParagraphDecoration();
                return pd != null && pd.hasTableDecoration();
            }
        }
        return false;
    }

    /**
     * Zaehlt Zellseparatoren (U+200B) in einem Text.
     */
    private static int countTableSeparators(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == com.gluonhq.richtextarea.model.TextBuffer.ZERO_WIDTH_TABLE_SEPARATOR) {
                count++;
            }
        }
        return count;
    }

    /**
     * Wendet eine Textdekoration auf einen Bereich an, ohne die umgebende
     * Formatierung zu beruehren (analog zur Toolbar-Formatierung F/K/U/S).
     */
    private void applyDecoration(int start, int end, TextDecoration decoration) {
        if (decoration == null || end <= start) {
            return;
        }
        control.getActionFactory()
                .selectAndDecorate(new Selection(start, end), decoration)
                .execute(new ActionEvent());
    }

    /**
     * Ersetzt einen einzelnen Treffer formatbewahrend:
     * 1. Original-Dekoration des Treffers merken,
     * 2. Text ersetzen (loeschen + einfügen),
     * 3. Dekoration auf den Ersatzbereich zurueckspielen.
     *
     * @return true wenn ersetzt, false wenn abgelehnt
     *         (Absatzgrenze oder Tabellen-Grenze verletzt).
     */
    private boolean replaceMatchSafely(Match match, String replacement) {
        int start = match.getStart();
        int end = match.getEnd();
        String fullText = getText();

        // Guard: Treffer darf keine Absatzgrenze enthalten (sonst wuerde
        // der Ersatz Absätze zusammenlegen oder zerteilen).
        if (fullText.substring(start, end).contains("\n")) {
            return false;
        }

        // Guard: Tabellen-Absatz darf nicht gekreuzt/gespalten werden.
        // Ersetzen innerhalb einer Zelle ist erlaubt (kein Separator,
        // kein '\n', gleiche Separator-Anzahl vor/nach dem Ersatz).
        if (isParagraphWithTable(start)) {
            int separatorsInMatch = countTableSeparators(fullText.substring(start, end));
            int separatorsInReplacement = countTableSeparators(replacement);
            if (separatorsInMatch > 0 || separatorsInReplacement > 0) {
                return false;
            }
            if (replacement.contains("\n")) {
                return false;
            }
        }

        // Dekoration des Treffers sichern, bevor der Text geloescht wird.
        TextDecoration trefferDeko = captureTextDecoration(start);

        // Ersatz durchfuehren (Auswahl loeschen, Ersatz einfügen).
        // Text- und Editor-Positionen sind fuer einfachen Text identisch
        // (wie der bisherige Code ohne Konvertierung gezeigt hat).
        control.getActionFactory()
                .selectAndInsertText(new Selection(start, end), replacement)
                .execute(new ActionEvent());

        // Dekoration auf den Ersatzbereich zurückspielen.
        applyDecoration(start, start + replacement.length(), trefferDeko);
        return true;
    }

    /**
     * Prueft, ob der Trefferbereich vollstaendig durchgestrichen formatiert
     * ist (Format-Durchstreichung analog LO). Leere Treffer zaehlen nie.
     */
    public boolean isFullyStrikethrough(Match match) {
        if (match == null || match.length() <= 0) {
            return false;
        }
        Document document = viewModel.getDocument();
        if (document == null || document.getDecorations() == null) {
            return false;
        }
        int start = match.getStart();
        int end = match.getEnd();
        for (DecorationModel model : document.getDecorations()) {
            if (model == null) {
                continue;
            }
            int fragStart = model.getStart();
            int fragEnd = fragStart + model.getLength();
            if (fragEnd <= start || fragStart >= end) {
                continue;
            }
            if (!isStrikethrough(model.getDecoration())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isStrikethrough(Decoration decoration) {
        return decoration instanceof TextDecoration td && Boolean.TRUE.equals(td.isStrikethrough());
    }

    /**
     * Prueft, ob der aktuelle Treffer ein leerer Treffer am Scope-Ende ist.
     * Solche Treffer beenden die Suche (sie koennen nicht ersetzt werden und
     * wuerden sonst ueber die Scope-Grenze hinausgreifen).
     */
    private static boolean isEmptyMatchAtScopeEnd(Matcher matcher, int scopeEnd) {
        return matcher.end() == matcher.start() && matcher.end() >= scopeEnd;
    }

    public int replaceAll(String searchText, String replaceText, FindOptions options) {
        FindOptions effective = options == null ? new FindOptions() : options;
        Pattern pattern = compilePattern(searchText, effective);
        if (pattern == null) {
            return 0;
        }
        String fullText = getText();
        int[] range = scope(fullText, effective);
        if (range == null) {
            return 0;
        }
        int scopeStart = range[0];
        int scopeEnd = range[1];
        String replacement = replaceText == null ? "" : replaceText;

        // Alle Treffer sammeln (von vorne nach hinten).
        List<Match> candidates = new ArrayList<>();
        Matcher matcher = pattern.matcher(fullText);
        matcher.region(scopeStart, scopeEnd);
        while (matcher.find()) {
            if (isEmptyMatchAtScopeEnd(matcher, scopeEnd)) {
                break;
            }
            if (matcher.end() == matcher.start()) {
                continue;
            }
            Match candidate = new Match(matcher.start(), matcher.end());
            if (effective.isStrikethroughOnly() && !isFullyStrikethrough(candidate)) {
                continue;
            }
            if (!respectsWholeWords(fullText, matcher.start(), matcher.end(), effective)) {
                continue;
            }
            candidates.add(candidate);
        }

        // Rueckwaerts ersetzen, damit Offsets gueltig bleiben.
        int replaced = 0;
        int skipped = 0;
        for (int i = candidates.size() - 1; i >= 0; i--) {
            Match candidate = candidates.get(i);
            String resolved = resolveReplacement(searchText, replacement, effective,
                    fullText.substring(candidate.getStart(), candidate.getEnd()));
            if (replaceMatchSafely(candidate, resolved)) {
                replaced++;
            } else {
                skipped++;
            }
        }

        if (replaced == 0 && skipped == 0) {
            return 0;
        }
        // Rueckmeldung an den Aufrufer: ersetzt + übersprungen.
        // Der Dialog wertet das aus und zeigt einen entsprechenden Status.
        lastReplacedCount = replaced;
        lastSkippedCount = skipped;
        return replaced;
    }

    private String resolveReplacement(String searchText, String replaceText, FindOptions options, String matched) {
        if (!options.isRegex() && !options.isWildcards()) {
            return replaceText;
        }
        Pattern pattern = compilePattern(searchText, options);
        if (pattern == null) {
            return replaceText;
        }
        Matcher matcher = pattern.matcher(matched);
        if (!matcher.matches()) {
            return replaceText;
        }
        return expandReplacement(replaceText, matcher, options);
    }

    private String expandReplacement(String template, Matcher matcher, FindOptions options) {
        if (!options.isRegex() && !options.isWildcards()) {
            return template;
        }
        String prepared = template;
        String group0;
        try {
            group0 = matcher.group();
        } catch (IllegalStateException e) {
            return template;
        }
        if (options.isWildcards() && !options.isRegex()) {
            prepared = prepared.replace("$0", Matcher.quoteReplacement(group0));
        }
        // Ersatz isoliert auswerten: frischer Matcher auf dem Treffertext,
        // damit appendReplacement keinen Bereichs-Prefix einbettet.
        // (Das Muster enthaelt KEINE \\b-Wrapper mehr – Wortgrenzen werden
        // als Nachfilter geprueft –, daher matcht es den Treffertext zuverlaessig.)
        try {
            Matcher single = matcher.pattern().matcher(group0);
            StringBuffer buffer = new StringBuffer();
            if (single.find()) {
                single.appendReplacement(buffer, prepared);
                return buffer.toString();
            }
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            return template;
        }
        return template;
    }

    public void selectMatch(Match match) {
        selectMatch(match, true);
    }

    public void selectMatch(Match match, boolean moveCaret) {
        if (match == null) {
            return;
        }
        Selection editorSelection = toEditorSelection(match.getStart(), match.getEnd());
        viewModel.setSelection(editorSelection);
        if (moveCaret) {
            viewModel.setCaretPosition(editorSelection.getEnd());
        }
        // Treffer in den Sichtbereich scrollen (Treffer-Ende, LO: „Ansprung“).
        // Beruehrt weder Selection noch Caret noch Fokus – nur den Viewport.
        revealHandler.accept(editorSelection.getEnd());
    }

    public void moveCaretToSelectionEnd() {
        Selection selection = getSelection();
        if (selection != null) {
            viewModel.setCaretPosition(selection.getEnd());
        }
    }

    public void clearMatchSelection() {
        viewModel.clearSelection();
    }
}
