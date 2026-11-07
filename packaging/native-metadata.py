#!/usr/bin/env python3
"""Supplement target-host traces with JavaFX's platform backends and all packaged resources.

Font factories, native callbacks and optional GPU pipelines are loaded by name.
Traces on virtual CI displays alone cannot cover every hardware backend.
"""
import argparse
import json
from pathlib import Path
import zipfile


BACKENDS = {
    "linux": ("com/sun/glass/ui/gtk/", "com/sun/javafx/font/freetype/", "com/sun/prism/es2/"),
    "win": ("com/sun/glass/ui/win/", "com/sun/javafx/font/directwrite/", "com/sun/prism/d3d/"),
    "mac": ("com/sun/glass/ui/mac/", "com/sun/javafx/font/coretext/", "com/sun/prism/es2/"),
    "mac-aarch64": ("com/sun/glass/ui/mac/", "com/sun/javafx/font/coretext/", "com/sun/prism/es2/"),
}

# JavaFX's Marlin renderer uses memory-segment VarHandles. Native Image's
# method-handle substitutions query these accessors even when the JVM agent
# records only the intrinsic access, so include their reflective members.
SEGMENT_TYPES = ('Booleans', 'Bytes', 'Chars', 'CharsAligned', 'Doubles',
                 'DoublesAligned', 'Floats', 'FloatsAligned', 'Ints',
                 'IntsAligned', 'Longs', 'LongsAligned', 'Shorts', 'ShortsAligned')


def metadata(input_dir, platform):
    reflection, resources = {}, {}
    for jar in sorted(Path(input_dir).glob("*.jar")):
        if jar.name.startswith("javafx-graphics-"):
            module = "javafx.graphics"
        elif jar.name.startswith("javafx-controls-"):
            module = "javafx.controls"
        elif jar.name.startswith("javafx-base-"):
            module = "javafx.base"
        elif jar.name.startswith("RichMarkDownEditor-"):
            module = "RichMarkDownEditor"
        elif jar.name.startswith("rich-text-area-"):
            module = "com.gluonhq.richtextarea"
        elif jar.name.startswith("emoji-noto-"):
            module = "com.gluonhq.emoji"
        else:
            raise ValueError(f"Unknown runtime module: {jar.name}")
        with zipfile.ZipFile(jar) as archive:
            for name in sorted(archive.namelist()):
                if name.endswith(".class"):
                    if module == "javafx.graphics" and name.startswith(BACKENDS[platform] + ("com/sun/prism/shader/",)):
                        kind = name[:-6].replace("/", ".")
                        reflection[kind] = {"type": kind, "jniAccessible": True,
                                            "allDeclaredConstructors": True, "allDeclaredMethods": True,
                                            "allDeclaredFields": True}
                    continue
                if not name.endswith("/") and not name.startswith("META-INF/"):
                    resources[(module, name)] = {"module": module, "glob": name}
                    if name.endswith(".css"):
                        # JavaFX probes a synthesized .bss URL before falling back
                        # to CSS. Exact metadata must also register this negative lookup.
                        binary = name[:-4] + ".bss"
                        resources[(module, binary)] = {"module": module, "glob": binary}
    if not reflection or not any("font" in name for name in reflection):
        raise ValueError(f"No {platform} JavaFX font backend in runtime input")
    for suffix in SEGMENT_TYPES:
        kind = 'java.lang.invoke.VarHandleSegmentAs' + suffix
        reflection[kind] = {'type': kind, 'allDeclaredMethods': True}
    # JUL probes localized bundles during JavaFX initialization, including
    # absent locale-specific variants before using the root bundle.
    resources[('java.logging', 'sun/util/logging/resources/**')] = {
        'module': 'java.logging', 'glob': 'sun/util/logging/resources/**'}
    # Preferences writes XML in a JVM shutdown hook. ServiceLoader's optional
    # provider lookup can happen after the agent has already written its trace.
    resources[('', 'META-INF/services/**')] = {'glob': 'META-INF/services/**'}
    return {"reflection": list(reflection.values()), "resources": list(resources.values())}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("platform", choices=BACKENDS)
    parser.add_argument("input_dir", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    data = metadata(args.input_dir, args.platform)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    print(f"Target backend metadata: {len(data['reflection'])} types, {len(data['resources'])} resources")


if __name__ == "__main__":
    main()
