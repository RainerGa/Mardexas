"""Ensure a Windows-style Git checkout preserves reviewed third-party bytes."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


PROJECT = Path(__file__).resolve().parent.parent


class CheckoutContractsTest(unittest.TestCase):
    def test_autocrlf_preserves_reviewed_licenses_and_icon_bytes(self):
        paths = [
            'editor/src/main/resources/de/richmarkdown/ui/icons/tabler/LICENSE',
            'editor/src/main/resources/de/richmarkdown/ui/icons/tabler/list-search.svg',
            'emoji-noto/src/main/resources/com/gluonhq/emoji/noto/LICENSE-NOTO-APACHE-2.0.txt',
            'packaging/licenses/javafx-25.0.4/win/javafx.base/LICENSE',
        ]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            shutil.copyfile(PROJECT / '.gitattributes', root / '.gitattributes')
            for name in paths:
                destination = root / name
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(PROJECT / name, destination)

            def git(*arguments):
                return subprocess.run(['git', '-C', str(root), *arguments],
                                      check=True, capture_output=True)

            git('init')
            git('-c', 'core.autocrlf=false', 'add', '--', '.gitattributes', *paths)
            for name in paths:
                (root / name).unlink()
            git('-c', 'core.autocrlf=true', '-c', 'core.eol=crlf',
                'checkout-index', '--all', '--force')
            for name in paths:
                with self.subTest(path=name):
                    self.assertEqual((PROJECT / name).read_bytes(), (root / name).read_bytes())


if __name__ == '__main__':
    unittest.main()
