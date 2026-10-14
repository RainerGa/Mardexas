package de.richmarkdown.ui;

import com.gluonhq.richtextarea.RichTextArea;
import com.gluonhq.richtextarea.model.ParagraphDecoration;
import com.gluonhq.richtextarea.model.TextDecoration;
import com.gluonhq.richtextarea.action.ParagraphDecorateAction;
import com.gluonhq.richtextarea.action.TextDecorateAction;
import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.markdown.MarkdownStyles;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Formatierungs-Toolbar des Editors mit Textformaten, Absatzstilen,
 * Listensteuerung und Einrückungsaktionen.
 *
 * <p>Die Toolbar beobachtet Caret- und Absatzdekorationen, um ihre Toggle-
 * und Dropdown-Zustände mit der aktuellen Auswahl zu synchronisieren.
 * Bedienaktionen werden an {@link MarkdownEditor} delegiert.
 *
 * <ul>
 *   <li>F/K/U/S-Toggles mit Caret-Zustand (bidirektionale Bindung über
 *       {@link TextDecorateAction} der Bibliothek); die Buchstaben sind
 *       typografisch ausgezeichnet (fett, kursiv, …)</li>
 *   <li>Styles-Dropdown: Absatz, H1–H6, Zitat</li>
 *   <li>Listen-Buttons (Aufzählung/Nummerierung) mit Vektor-Icons über
 *       {@link ParagraphDecorateAction}</li>
 *   <li>Einzug verkleinern/vergrößern mit Vektor-Icons über die Editor-Fassade</li>
 * </ul>
 */
public class FormatToolBar extends ToolBar {

    /** Schriftgröße der Buchstaben-Toggles (F/K/U/S). */
    public static final double LETTER_TEXT_SIZE = 13.0;

    /** Absatzstile des Dropdowns. */
    public enum ParagraphStyle {
        /** Standardformat für Fließtext. */
        BODY("style.body"),
        /** Überschriftenformat der ersten Ebene. */
        H1("style.heading1"),
        /** Überschriftenformat der zweiten Ebene. */
        H2("style.heading2"),
        /** Überschriftenformat der dritten Ebene. */
        H3("style.heading3"),
        /** Überschriftenformat der vierten Ebene. */
        H4("style.heading4"),
        /** Überschriftenformat der fünften Ebene. */
        H5("style.heading5"),
        /** Überschriftenformat der sechsten Ebene. */
        H6("style.heading6"),
        /** Absatzformat für Blockzitate. */
        QUOTE("style.quote");

        private final String labelKey;

        ParagraphStyle(String labelKey) {
            this.labelKey = labelKey;
        }

        /**
         * Liefert die Übersetzung des jeweiligen Absatzstils.
         *
         * @return aktuelles lokalisiertes Label
         */
        public String getLabel() {
            return I18n.text(labelKey);
        }

        /** Liefert das lokalisierte Label, das auch in der ChoiceBox erscheint. */
        @Override
        public String toString() {
            return getLabel();
        }
    }

    private final MarkdownEditor editor;
    private final RichTextArea control;

    private final ToggleButton boldToggle = createLetterToggle("B", "toolbar.bold",
            FontWeight.BOLD, FontPosture.REGULAR, false, false, "rme-toggle-bold");
    private final ToggleButton italicToggle = createLetterToggle(
            "de".equals(I18n.locale().getLanguage()) ? "K" : "I", "toolbar.italic",
            FontWeight.NORMAL, FontPosture.ITALIC, false, false, "rme-toggle-italic");
    private final ToggleButton underlineToggle = createLetterToggle("U", "toolbar.underline",
            FontWeight.NORMAL, FontPosture.REGULAR, true, false, "rme-toggle-underline");
    private final ToggleButton strikethroughToggle = createLetterToggle("S", "toolbar.strikethrough",
            FontWeight.NORMAL, FontPosture.REGULAR, false, true, "rme-toggle-strikethrough");
    private final ToggleButton inlineCodeToggle = createIconToggle(
            ToolbarIcons.inlineCodeIcon(), I18n.text("toolbar.inlineCode"));
    private final ToggleButton bulletListToggle = createIconToggle(
            ToolbarIcons.bulletListIcon(), I18n.text("toolbar.bulletList"));
    private final ToggleButton numberedListToggle = createIconToggle(
            ToolbarIcons.numberedListIcon(), I18n.text("toolbar.numberedList"));
    private final ChoiceBox<ParagraphStyle> styleChoice = new ChoiceBox<>();
    private final Button indentButton = createIconButton(
            ToolbarIcons.indentIcon(), I18n.text("toolbar.indent"));
    private final Button outdentButton = createIconButton(
            ToolbarIcons.outdentIcon(), I18n.text("toolbar.outdent"));

    private boolean applyingStyle;

    /**
     * Erstellt eine Formatierungsleiste für den angegebenen Editor.
     *
     * @param editor Editor-Fassade, deren Control beobachtet und bearbeitet wird
     */
    public FormatToolBar(MarkdownEditor editor) {
        this.editor = editor;
        this.control = editor.getControl();

        getStyleClass().add("rme-toolbar");

        indentButton.setOnAction(event -> editor.indent());
        outdentButton.setOnAction(event -> editor.outdent());

        styleChoice.getStyleClass().add("rme-style-choice");
        styleChoice.getItems().addAll(ParagraphStyle.values());
        styleChoice.setValue(ParagraphStyle.BODY);
        styleChoice.valueProperty().addListener((obs, old, newValue) -> {
            if (newValue == null || applyingStyle) {
                return;
            }
            applyingStyle = true;
            applyStyle(newValue);
            // Guard nach der asynchronen Anwendung zurücksetzen
            Platform.runLater(() -> applyingStyle = false);
        });

        bindTextToggles();
        bindListToggles();
        bindCaretState();

        getItems().addAll(
                styleChoice,
                boldToggle,
                italicToggle,
                underlineToggle,
                strikethroughToggle,
                inlineCodeToggle,
                separator(),
                bulletListToggle,
                numberedListToggle,
                separator(),
                outdentButton,
                indentButton);
    }

    // ---------- Zugriff für Tests/UI ----------

    /** Liefert den an die aktuelle Caret-Formatierung gebundenen Fettschrift-Umschalter.
     *
     * @return Umschalter für Fettschrift
     */
    public ToggleButton getBoldToggle() {
        return boldToggle;
    }

    /** Liefert den an die aktuelle Caret-Formatierung gebundenen Kursiv-Umschalter.
     *
     * @return Umschalter für Kursivschrift
     */
    public ToggleButton getItalicToggle() {
        return italicToggle;
    }

    /** Liefert den Unterstreichungs-Umschalter.
     *
     * @return Umschalter für Unterstreichung
     */
    public ToggleButton getUnderlineToggle() {
        return underlineToggle;
    }

    /** Liefert den Durchstreichungs-Umschalter.
     *
     * @return Umschalter für Durchstreichung
     */
    public ToggleButton getStrikethroughToggle() {
        return strikethroughToggle;
    }

    /** Liefert den Umschalter für monospaced Inline-Code-Formatierung.
     *
     * @return Umschalter für Inline-Code-Formatierung
     */
    public ToggleButton getInlineCodeToggle() {
        return inlineCodeToggle;
    }

    /** Liefert den Umschalter für Aufzählungslisten.
     *
     * @return Umschalter für Aufzählungslisten
     */
    public ToggleButton getBulletListToggle() {
        return bulletListToggle;
    }

    /** Liefert den Umschalter für nummerierte Listen.
     *
     * @return Umschalter für nummerierte Listen
     */
    public ToggleButton getNumberedListToggle() {
        return numberedListToggle;
    }

    /** Liefert die Auswahlbox für Absatzformat, Überschriftenebene oder Zitat.
     *
     * @return Auswahlbox für Absatz, Überschriften und Zitat
     */
    public ChoiceBox<ParagraphStyle> getStyleChoice() {
        return styleChoice;
    }

    /** Liefert die Schaltfläche zum Vergrößern des Absatz- beziehungsweise Listeneinzugs.
     *
     * @return Schaltfläche zum Vergrößern des Einzugs
     */
    public Button getIndentButton() {
        return indentButton;
    }

    /** Liefert die Schaltfläche zum Verringern des Absatz- beziehungsweise Listeneinzugs.
     *
     * @return Schaltfläche zum Verringern des Einzugs
     */
    public Button getOutdentButton() {
        return outdentButton;
    }

    // ---------- Interna ----------

    /** Wort-/Icon-lose Trennung zwischen Button-Gruppen (wie in Word). */
    private Node separator() {
        Separator separator = new Separator();
        separator.getStyleClass().add("rme-toolbar-separator");
        return separator;
    }

    /**
     * Buchstaben-Toggle (F/K/U/S) mit echter Typografie: der Buchstabe wird als
     * {@link Text} gesetzt, damit Schnitt (fett/kursiv) sowie Unterstrich und
     * Durchstrich so gerendert werden wie in Word/LibreOffice – unabhängig von
     * CSS-Textfarbe.
     */
    private ToggleButton createLetterToggle(String letter, String tooltipKey, FontWeight weight,
                                            FontPosture posture, boolean underline,
                                            boolean strikethrough, String extraStyleClass) {
        Text text = new Text(letter);
        text.setFont(fontOf(weight, posture));
        text.setUnderline(underline);
        text.setStrikethrough(strikethrough);
        text.getStyleClass().add("rme-toggle-letter");
        ToggleButton button = new ToggleButton();
        button.setGraphic(text);
        button.getStyleClass().addAll("rme-toolbar-button", extraStyleClass);
        button.setTooltip(new Tooltip(I18n.text(tooltipKey)));
        button.setFocusTraversable(false);
        return button;
    }

    private javafx.scene.text.Font fontOf(FontWeight weight, FontPosture posture) {
        return javafx.scene.text.Font.font(
                javafx.scene.text.Font.getDefault().getFamily(), weight, posture, LETTER_TEXT_SIZE);
    }

    private ToggleButton createIconToggle(Node icon, String tooltip) {
        ToggleButton button = new ToggleButton();
        button.setGraphic(icon);
        button.getStyleClass().add("rme-toolbar-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
        return button;
    }

    private Button createIconButton(Node icon, String tooltip) {
        Button button = new Button();
        button.setGraphic(icon);
        button.getStyleClass().add("rme-toolbar-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
        return button;
    }

    /**
     * F/K/U/S: bidirektionale Bindung zwischen Toggle-Zustand und Caret-Dekoration.
     * Die Bibliotheks-Action wendet das Decoration auf die Selektion (bzw. als
     * decorationAtCaret ohne Selektion) an und aktualisiert den Zustand bei
     * Caret-Bewegungen.
     */
    private void bindTextToggles() {
        bindTextToggle(boldToggle,
                td -> td.getFontWeight() == FontWeight.BOLD,
                (builder, value) -> builder.fontWeight(value ? FontWeight.BOLD : FontWeight.NORMAL).build());

        bindTextToggle(italicToggle,
                td -> td.getFontPosture() == FontPosture.ITALIC,
                (builder, value) -> builder.fontPosture(value ? FontPosture.ITALIC : FontPosture.REGULAR).build());

        bindTextToggle(underlineToggle,
                td -> Boolean.TRUE.equals(td.isUnderline()),
                (builder, value) -> builder.underline(value).build());

        bindTextToggle(strikethroughToggle,
                td -> Boolean.TRUE.equals(td.isStrikethrough()),
                (builder, value) -> builder.strikethrough(value).build());

        // Value-Function liefert den IST-Zustand (true = Inline-Code aktiv)
        bindTextToggle(inlineCodeToggle,
                td -> MarkdownStyles.MONOSPACED.equals(td.getFontFamily()),
                (builder, value) -> builder.fontFamily(
                        value ? MarkdownStyles.MONOSPACED : defaultFontFamily()).build());
    }

    private void bindTextToggle(ToggleButton toggle,
                                java.util.function.Function<TextDecoration, Boolean> valueFunction,
                                java.util.function.BiFunction<TextDecoration.Builder, Boolean, TextDecoration> builderFunction) {
        ObjectProperty<Boolean> valueProperty = new SimpleObjectProperty<>(false);
        new TextDecorateAction<>(control, valueProperty, valueFunction, builderFunction);
        toggle.selectedProperty().bindBidirectional(valueProperty);
    }

    /** Default-Schriftfamilie der Bibliothek (für das Abschalten von Inline-Code). */
    private String defaultFontFamily() {
        return TextDecoration.builder().presets().build().getFontFamily();
    }

    /** Listen-Buttons über ParagraphDecorateAction (Caret-Zustand inklusive). */
    private void bindListToggles() {
        bindListToggle(bulletListToggle, ParagraphDecoration.GraphicType.BULLETED_LIST);
        bindListToggle(numberedListToggle, ParagraphDecoration.GraphicType.NUMBERED_LIST);
    }

    private void bindListToggle(ToggleButton toggle, ParagraphDecoration.GraphicType graphicType) {
        ObjectProperty<Boolean> valueProperty = new SimpleObjectProperty<>(false);
        new ParagraphDecorateAction<>(control, valueProperty,
                pd -> pd.getGraphicType() == graphicType,
                (builder, value) -> value
                        ? builder.graphicType(graphicType).build()
                        : builder.presets().build());
        toggle.selectedProperty().bindBidirectional(valueProperty);
    }

    /** Dropdown folgt dem Caret-Zustand (Überschrift/Zitat/Absatz). */
    private void bindCaretState() {
        control.decorationAtCaretProperty().addListener((obs, old, nv) -> updateStyleFromCaret());
        control.decorationAtParagraphProperty().addListener((obs, old, nv) -> updateStyleFromCaret());
    }

    private void updateStyleFromCaret() {
        if (applyingStyle) {
            return;
        }
        ParagraphStyle detected = detectStyle();
        if (detected != styleChoice.getValue()) {
            // Guard setzen, BEVOR der Wert gesetzt wird: der Listener würde das
            // erkannte Format sonst auf das Dokument anwenden (Feedback-Schleife),
            // statt es nur anzuzeigen. Genau dieses Anwenden hat bisher z. B. ein
            // Zitat auf „Absatz" zurückgesetzt, wenn der Caret dorthin sprang.
            applyingStyle = true;
            try {
                styleChoice.setValue(detected);
            } finally {
                applyingStyle = false;
            }
        }
    }

    private ParagraphStyle detectStyle() {
        return CaretStyle.detect(control);
    }

    private void applyStyle(ParagraphStyle style) {
        switch (style) {
            case BODY -> editor.applyParagraphStyle();
            case QUOTE -> editor.applyQuote();
            case H1 -> editor.applyHeading(1);
            case H2 -> editor.applyHeading(2);
            case H3 -> editor.applyHeading(3);
            case H4 -> editor.applyHeading(4);
            case H5 -> editor.applyHeading(5);
            case H6 -> editor.applyHeading(6);
            default -> throw new IllegalStateException("unexpected style: " + style);
        }
    }
}
