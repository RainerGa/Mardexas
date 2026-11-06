package de.richmarkdown.io;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TemplateFieldsTest {
    @Test void fieldNamesAreUniqueOrderedAndImmutable() {
        var names = TemplateFields.names("# ${Titel}\n${Name} ${Titel}");
        assertEquals(List.of("Titel", "Name"), names);
        assertThrows(UnsupportedOperationException.class, () -> names.clear());
    }
    @Test void substitutionIsLiteralNonRecursiveAndLeavesMarkdownIntact() {
        assertEquals("# **Überschrift**\n$1\\ ${another} $1\\", TemplateFields.resolve(
                "# **Überschrift**\n${Name} ${Other} ${Name}", Map.of("Name", "$1\\", "Other", "${another}")));
        assertEquals("ordinary", TemplateFields.resolve("ordinary", Map.of()));
    }
    @Test void emptyValuesAllowedButMissingValuesRejected() {
        assertEquals("", TemplateFields.resolve("${Name}", Map.of("Name", "")));
        assertThrows(IllegalArgumentException.class, () -> TemplateFields.resolve("${Name}", Map.of()));
    }
    @Test void malformedSyntaxIsOrdinaryTextAndFieldCountBounded() {
        assertEquals(List.of(), TemplateFields.names("${} ${a\nb} ${unterminated"));
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 65; i++) content.append("${field").append(i).append('}');
        assertThrows(IllegalArgumentException.class, () -> TemplateFields.names(content.toString()));
    }
}
