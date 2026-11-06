"""Run with python3 -m unittest discover -s packaging -p 'test_audit_icons.py'."""
import contextlib
import importlib.util
import io
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SPEC = importlib.util.spec_from_file_location("audit_icons", Path(__file__).with_name("audit-icons.py"))
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)


class IconAuditTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.source = self.directory / "icons"
        shutil.copytree(AUDIT.SOURCE, self.source)
        self.patcher = patch.object(AUDIT, "SOURCE", self.source)
        self.patcher.start()
        self.addCleanup(self.patcher.stop)
        self.output = contextlib.redirect_stdout(io.StringIO())
        self.output.__enter__()
        self.addCleanup(self.output.__exit__, None, None, None)

    def jar(self, omit=None, replace=None):
        result = self.directory / "application.jar"
        with zipfile.ZipFile(result, "w") as archive:
            for path in self.source.iterdir():
                if path.name != omit:
                    archive.writestr(AUDIT.PREFIX + path.name,
                                     b"altered" if path.name == replace else path.read_bytes())
        return result

    def test_verified_source_and_complete_distribution_pass(self):
        AUDIT.audit(jar=self.jar())

    def test_changed_copyright_notice_is_rejected(self):
        (self.source / "LICENSE").write_text("MIT License without attribution")
        with self.assertRaisesRegex(ValueError, "checksum mismatch: LICENSE"):
            AUDIT.audit()

    def test_new_unreviewed_icon_is_rejected(self):
        (self.source / "new.svg").write_text("<svg/>")
        with self.assertRaisesRegex(ValueError, "inventory"):
            AUDIT.audit()

    def test_distribution_without_license_is_rejected(self):
        with self.assertRaises(KeyError):
            AUDIT.audit(jar=self.jar(omit="LICENSE"))

    def test_changed_distributed_license_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "distribution resource: LICENSE"):
            AUDIT.audit(jar=self.jar(replace="LICENSE"))

    def test_unexpected_upstream_is_rejected(self):
        archive = self.directory / "wrong.tar.gz"
        archive.write_bytes(b"wrong release")
        with self.assertRaisesRegex(ValueError, "Unexpected upstream archive"):
            AUDIT.audit(upstream=archive)
