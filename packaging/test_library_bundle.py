import hashlib
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
import zipfile


def load(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


bundle = load('library-bundle')
demo = load('run-demo')


class LibraryBundleTest(unittest.TestCase):
    def fixture(self, root):
        for directory, group, artifact, version in (
                ('editor', 'de.richmarkdown', 'RichMarkDownEditor', '1.0.0'),
                ('rich-text-area/rta', 'com.gluonhq', 'rich-text-area', '3.0-RG'),
                ('emoji-noto', 'de.richmarkdown', 'emoji-noto', '1.1.0-mardexas-1')):
            target = root / directory / 'target'
            target.mkdir(parents=True)
            (target.parent / 'pom.xml').write_text(f'<project><groupId>{group}</groupId><artifactId>{artifact}</artifactId><version>{version}</version></project>')
            with zipfile.ZipFile(target / f'{artifact}-{version}.jar', 'w') as archive:
                for name in ('module-info.class', 'de/richmarkdown/api/EditorPane.class', 'de/richmarkdown/api/EditorMenus.class'):
                    archive.writestr(name, b'bytecode')
        for name in ('README.md', 'LICENSE', 'THIRD-PARTY-NOTICES.md', 'docs/API.md', 'docs/COMMANDS.md',
                     'docs/PACKAGING.md', 'docs/VALIDATION.md',
                     'integration-demos/pom.xml', 'integration-demos/src/main/java/Demo.java',
                     'integration-demos/target/ignored.class', 'editor/target/api-docs/index.html',
                     'packaging/licenses/javafx-25.0.4/ORIGIN.json'):
            target = root / name; target.parent.mkdir(parents=True, exist_ok=True); target.write_text('content')

    def test_repository_all_custom_artifacts_javadoc_demos_notices_and_no_build_outputs(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); self.fixture(root)
            destination = root / 'dist/library.zip'
            count = bundle.create_bundle(root, destination)
            with zipfile.ZipFile(destination) as archive:
                names = archive.namelist()
                self.assertGreaterEqual(count, 15)
                self.assertIn('Mardexas-library/repository/de/richmarkdown/RichMarkDownEditor/1.0.0/RichMarkDownEditor-1.0.0.pom', names)
                self.assertIn('Mardexas-library/javadoc/index.html', names)
                self.assertIn('Mardexas-library/docs/COMMANDS.md', names)
                self.assertIn('Mardexas-library/integration-demos/src/main/java/Demo.java', names)
                self.assertFalse(any('ignored.class' in name for name in names))
                self.assertEqual(3, sum(name.endswith('.jar') for name in names))
                self.assertEqual(3, sum(name.endswith('.pom') for name in names))
            self.assertFalse(destination.with_suffix('.zip.tmp').exists())

    def test_missing_javadoc_missing_artifact_and_shaded_jar_fail_without_replacing_previous_release(self):
        for broken in ('editor/target/api-docs/index.html', 'emoji-noto/target/emoji-noto-1.1.0-mardexas-1.jar', 'API'):
            with self.subTest(broken=broken), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary); self.fixture(root)
                destination = root / 'release.zip'; destination.write_bytes(b'previous')
                if broken == 'API':
                    with zipfile.ZipFile(root / 'editor/target/RichMarkDownEditor-1.0.0.jar', 'w') as archive:
                        archive.writestr('a.class', b'fat-jar')
                else:
                    (root / broken).unlink()
                with self.assertRaises(ValueError):
                    bundle.create_bundle(root, destination)
                self.assertEqual(b'previous', destination.read_bytes())

    def test_external_demo_launcher_uses_platform_module_path_and_forwards_only_arguments(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for name in ('editor/target/RichMarkDownEditor-1.0.0.jar', 'editor/target/RichMarkDownEditor-1.0.0-fat.jar',
                         'integration-demos/target/mardexas-integration-demos-1.0.0.jar',
                         'editor/target/library-input/javafx-controls-25.0.4-linux.jar'):
                target = root / name; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(b'jar')
            command = demo.command(root, ['--case=RIBBON'], root / 'jdk with spaces')
            self.assertIn('jdk with spaces', command[0])
            self.assertEqual('--case=RIBBON', command[-1])
            self.assertEqual('mardexas.integration.demos/de.richmarkdown.demo.IntegrationDemo', command[-2])
            path = command[command.index('--module-path') + 1]
            self.assertEqual(3, len(path.split(os.pathsep)))
            self.assertNotIn('-fat.jar', path)
            (root / 'editor/target/library-input/javafx-controls-25.0.4-linux.jar').unlink()
            with self.assertRaisesRegex(ValueError, 'Missing JavaFX'):
                demo.command(root, [])


if __name__ == '__main__':
    unittest.main()
