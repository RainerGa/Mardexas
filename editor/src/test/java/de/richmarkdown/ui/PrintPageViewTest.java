package de.richmarkdown.ui;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.features.printing.PrintOptions;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PrintPageViewTest {
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @Test void headerFooterAndPageNumbersReserveSpaceAndClipBody() {
        FxTestUtil.runAndWait(() -> {
            var options = new PrintOptions(20,20,20,20,"System",14,"Header","Footer",true,PrintOptions.Scope.DOCUMENT,"");
            Label content = new Label("Document"); var page = new PrintPageView(content, 500, 700, options);
            assertEquals(652, page.contentHeight()); page.showPage(3, 7);
            assertEquals(-1304, content.getTranslateY()); assertEquals("Footer · 3 / 7", page.footerText());
            assertNotNull(content.getParent().getClip()); assertEquals("Document", content.getText());
            assertThrows(IllegalArgumentException.class, () -> page.showPage(0, 7));
        });
    }
    @Test void plainPagesDoNotReserveUnusedDecorationSpace() {
        var options = new PrintOptions(0,0,0,0,"System",14,"","",false,PrintOptions.Scope.DOCUMENT,"");
        assertEquals(700, PrintPageView.bodyHeight(700, options));
        assertThrows(IllegalArgumentException.class, () -> PrintPageView.bodyHeight(20, options));
    }
}
