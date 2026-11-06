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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

record ImagePlaceholder(String url, String link, int width, int height) {

    private static final String PREFIX = "missing-image:";
    private static final String NULL_VALUE = "\u0001";

    String displayText() {
        return "[Bild nicht gefunden: " + url + "]";
    }

    String marker() {
        String value = url + '\0' + (link == null ? NULL_VALUE : link)
                + '\0' + width + '\0' + height;
        return PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    static Optional<ImagePlaceholder> fromMarker(String marker) {
        if (marker == null || !marker.startsWith(PREFIX)) {
            return Optional.empty();
        }
        try {
            String value = new String(Base64.getUrlDecoder()
                    .decode(marker.substring(PREFIX.length())), StandardCharsets.UTF_8);
            String[] parts = value.split("\0", -1);
            if (parts.length != 4) {
                return Optional.empty();
            }
            String link = NULL_VALUE.equals(parts[1]) ? null : parts[1];
            return Optional.of(new ImagePlaceholder(parts[0], link,
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
