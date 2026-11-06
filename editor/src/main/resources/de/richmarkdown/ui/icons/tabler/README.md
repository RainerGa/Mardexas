# Tabler Icons local source subset

Source release: **v3.34.1**, https://github.com/tabler/tabler-icons/tree/v3.34.1
Originals: `icons/outline/<name>.svg` in that release. Copyright (c) 2020-2024 Paweł Kuna.
License: MIT; original complete text is included as `LICENSE`.

All 143 upstream SVGs and LICENSE were compared byte-for-byte with that release.
`upstream-sha256.properties` records their verified SHA-256 hashes. The additional
`gallery.svg` is a local arrangement. See `editor/ICON_LICENSE_AUDIT.md` in the
source repository for the GPL/MIT compatibility comparison and repeatable audit.

`commands.properties` explicitly maps semantic command IDs to the original icon
names. `+` combines a main motif and a smaller lower-right semantic badge.
`paths.properties` contains their SVG path geometry for JavaFX SVGPath. SVG circles
are normalized to equivalent arc paths. Invisible canvas paths (stroke=none) are
omitted. No external loader, font or network connection is used at runtime.
Sizes and colors are adapted by JavaFX; original SVG files retain upstream metadata.

Recent-file entries compose the original file-time icon with a small numerical
vector badge generated from simple line segments by Mardexas. Those number paths
are original project geometry, GPL-3.0-or-later, Copyright (C) 2026 Rainer Garbotz.
Other compositions likewise arrange these MIT-licensed source shapes without
changing their geometry. No Microsoft artwork or branding is included.
