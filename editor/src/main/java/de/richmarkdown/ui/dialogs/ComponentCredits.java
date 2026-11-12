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

package de.richmarkdown.ui.dialogs;

import java.util.List;

/** Shared, immutable attribution inventory for the license and thank-you windows. */
public final class ComponentCredits {
    public enum Group { APPLICATION, RUNTIME, ICONS, DEVELOPMENT }

    public record Component(String id, Group group, String name, String version,
                            String creators, String license, String source, String licenseUrl) { }

    private static final String GPL = "https://www.gnu.org/licenses/gpl-3.0.html";
    private static final String APACHE = "https://www.apache.org/licenses/LICENSE-2.0";
    private static final String CLASSPATH = "https://openjdk.org/legal/gplv2+ce.html";
    private static final List<Component> COMPONENTS = List.of(
        new Component("application", Group.APPLICATION, "Mardexas", "1.1.0", "Rainer Garbotz",
            "GPL-3.0-or-later", "https://github.com/RainerGa/Mardexas", GPL),
        new Component("rich-text-area", Group.RUNTIME, "RichTextArea fork", "3.5-RG",
            "Gluon / Rainer Garbotz", "GPL-3.0",
            "https://github.com/RainerGa/rich-text-area", GPL),
        new Component("emoji", Group.RUNTIME, "Mardexas Noto Emoji (Gluon fork)", "1.1.0-mardexas-1", "Gluon / Rainer Garbotz",
            "GPL-3.0-or-later", "https://github.com/gluonhq/emoji", GPL),
        new Component("openjfx", Group.RUNTIME, "OpenJFX Controls / Graphics / Base", "25.0.4",
            "OpenJFX", "GPL-2.0-only + Classpath Exception", "https://openjfx.io/", CLASSPATH),
        new Component("tabler", Group.ICONS, "Tabler Icons", "3.34.1", "Paweł Kuna",
            "MIT", "https://github.com/tabler/tabler-icons/tree/v3.34.1",
            "https://github.com/tabler/tabler-icons/blob/v3.34.1/LICENSE"),
        new Component("noto", Group.ICONS, "Noto Emoji (Google-only clean sheets)", "2.038",
            "Google LLC / Noto Emoji contributors", "Apache-2.0; region flags: public domain / copyright exemptions",
            "https://github.com/googlefonts/noto-emoji/tree/v2.038",
            "https://github.com/googlefonts/noto-emoji/blob/v2.038/LICENSE"),
        new Component("emoji-data", Group.ICONS, "emoji-data (Unicode data / clean sheet layout)", "063f328d7951",
            "Cal Henderson", "MIT", "https://github.com/iamcal/emoji-data/tree/063f328d7951cb2e2a6678b06dcbdf8dd599fad7",
            "https://github.com/iamcal/emoji-data/blob/063f328d7951cb2e2a6678b06dcbdf8dd599fad7/LICENSE"),
        new Component("junit", Group.DEVELOPMENT, "JUnit Jupiter / Platform", "5.11.3 / 1.11.3", "JUnit",
            "EPL-2.0", "https://github.com/junit-team/junit5", "https://www.eclipse.org/legal/epl-2.0/"),
        new Component("opentest4j", Group.DEVELOPMENT, "OpenTest4J", "1.3.0", "OpenTest4J",
            "Apache-2.0", "https://github.com/ota4j-team/opentest4j", APACHE),
        new Component("apiguardian", Group.DEVELOPMENT, "API Guardian", "1.1.2", "API Guardian",
            "Apache-2.0", "https://github.com/apiguardian-team/apiguardian", APACHE),
        new Component("testfx", Group.DEVELOPMENT, "TestFX Core / JUnit 5 integration", "4.0.19-gluon",
            "TestFX / Gluon", "EUPL-1.1", "https://github.com/TestFX/TestFX",
            "https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-11"),
        new Component("hamcrest", Group.DEVELOPMENT, "Hamcrest", "3.0", "Hamcrest",
            "BSD-3-Clause", "https://github.com/hamcrest/JavaHamcrest",
            "https://github.com/hamcrest/JavaHamcrest/blob/v3.0/LICENSE.txt"),
        new Component("assertj", Group.DEVELOPMENT, "AssertJ Core", "3.26.3", "AssertJ",
            "Apache-2.0", "https://github.com/assertj/assertj", APACHE),
        new Component("byte-buddy", Group.DEVELOPMENT, "Byte Buddy", "1.14.18", "Rafael Winterhalter / Byte Buddy",
            "Apache-2.0", "https://github.com/raphw/byte-buddy", APACHE),
        new Component("osgi", Group.DEVELOPMENT, "OSGi Core", "8.0.0", "OSGi",
            "Apache-2.0", "https://github.com/osgi/osgi", APACHE),
        new Component("monocle", Group.DEVELOPMENT, "OpenJFX Monocle", "21.0.2", "OpenJFX / TestFX",
            "GPL-2.0-only + Classpath Exception", "https://github.com/TestFX/Monocle", CLASSPATH)
    );

    private ComponentCredits() { }

    public static List<Component> all() { return COMPONENTS; }

    public static List<Component> in(Group group) {
        return COMPONENTS.stream().filter(component -> component.group() == group).toList();
    }
}
