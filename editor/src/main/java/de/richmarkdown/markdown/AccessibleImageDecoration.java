package de.richmarkdown.markdown;

import com.gluonhq.richtextarea.model.ImageDecoration;
import java.util.Objects;

/** Image metadata carried by the document, including undo, clipboard and file round trips. */
public final class AccessibleImageDecoration extends ImageDecoration {
    private final String altText;
    public AccessibleImageDecoration(String url, int width, int height, String link, String altText) {
        super(url, width, height, link);
        this.altText = Objects.requireNonNullElse(altText, "");
    }
    public String getAltText() { return altText; }
    public static String alt(ImageDecoration image) {
        return image instanceof AccessibleImageDecoration accessible ? accessible.altText : "";
    }
    @Override public boolean equals(Object other) {
        return other instanceof AccessibleImageDecoration image
                && getWidth() == image.getWidth() && getHeight() == image.getHeight()
                && Objects.equals(getUrl(), image.getUrl()) && Objects.equals(getLink(), image.getLink())
                && altText.equals(image.altText);
    }
    @Override public int hashCode() { return Objects.hash(getUrl(), getWidth(), getHeight(), getLink(), altText); }
}
