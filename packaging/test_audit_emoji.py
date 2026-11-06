"""Regression tests: fail closed on changed artwork, lost notices and old loaders."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("audit_emoji", Path(__file__).with_name("audit-emoji.py"))
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class EmojiAuditTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.expected = audit.audit_sources()

    def jar(self, omit=None, changed=None, extra=None, target="application.jar"):
        result = self.directory / target
        with zipfile.ZipFile(result, "w") as archive:
            for name in [*self.expected, "noto/sha256.json"]:
                if name != omit:
                    data = (audit.ROOT / audit.ASSETS / name).read_bytes()
                    archive.writestr(audit.PREFIX + name, b"changed" if name == changed else data)
            archive.writestr(audit.PREFIX + "NotoEmojiSpriteLoader.class", b"test class")
            for name, data in (extra or {}).items():
                archive.writestr(name, data)
        return result

    def test_reviewed_sources_and_complete_jar_pass(self):
        self.assertTrue(audit.audit_jar(self.jar(), self.expected))

    def test_changed_artwork_is_rejected_even_with_same_filename(self):
        for size in (20, 32, 64):
            with self.subTest(size=size), self.assertRaisesRegex(ValueError, "changed packaged"):
                audit.audit_jar(self.jar(changed=f"noto/sheet_google_{size}_clean.png"), self.expected)

    def test_all_original_notices_are_required(self):
        for name in (key for key in self.expected if key.endswith(".txt")):
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, "Missing or changed"):
                audit.audit_jar(self.jar(omit=name), self.expected)

    def test_old_loader_apple_sprites_and_unreviewed_mixed_sheets_are_rejected(self):
        for name in (audit.PREFIX + "offline/LocalEmojiSpriteLoader.class",
                     audit.PREFIX + "DownloadableEmojiSpriteLoader.class",
                     audit.PREFIX + "offline/sheet_apple_32.png",
                     audit.PREFIX + "noto/sheet_google_32.png",
                     "assets/sheet_apple_20.png",
                     "META-INF/maven/com.gluonhq.emoji/emoji/pom.properties"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                audit.audit_jar(self.jar(extra={name: b"unreviewed"}), self.expected)

    def test_manifest_tampering_and_missing_resources_are_rejected(self):
        for kind in ("omit", "changed"):
            with self.subTest(kind=kind), self.assertRaises(ValueError):
                audit.audit_jar(self.jar(**{kind: "noto/sha256.json"}), self.expected)

    def test_dependency_directory_requires_one_provider_and_rejects_old_extra_jar(self):
        with self.assertRaises(ValueError):
            audit.audit_directory(self.directory, self.expected)
        self.jar()
        audit.audit_directory(self.directory, self.expected)
        with zipfile.ZipFile(self.directory / "old-offline.jar", "w") as archive:
            archive.writestr(audit.PREFIX + "offline/sheet_apple_20.png", b"old artwork")
        with self.assertRaisesRegex(ValueError, "Forbidden old"):
            audit.audit_directory(self.directory, self.expected)

    def test_two_providers_and_a_thin_jar_alone_are_rejected(self):
        with zipfile.ZipFile(self.directory / "thin.jar", "w") as archive:
            archive.writestr("de/richmarkdown/Launcher.class", b"app")
        with self.assertRaises(ValueError):
            audit.audit_directory(self.directory, self.expected)
        self.jar()
        self.jar(target="duplicate-provider.jar")
        with self.assertRaisesRegex(ValueError, "exactly one"):
            audit.audit_directory(self.directory, self.expected)


if __name__ == "__main__":
    unittest.main()
