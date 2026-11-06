import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile


def load(name):
    spec = importlib.util.spec_from_file_location(name.replace('-', '_'), Path(__file__).with_name(name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


verify = load('verify-package')
metadata = load('native-metadata')


class RenderingComparisonTest(unittest.TestCase):
    def report(self):
        values = dict(sampleCount='7', os='Windows 11', architecture='amd64',
                      outputScaleX='1.0', outputScaleY='1.0', editorTextLength='120')
        for index in range(7):
            prefix = f'sample.{index}.'
            for key, value in dict(font='Segoe UI', size='14', width='330', height='18',
                                   **{'ink.1.0': '1200', 'ink.1.5': '2600', 'ink.2.0': '4800'}).items():
                values[prefix + key] = value
        return values

    def test_accepts_matching_fonts_and_small_raster_differences(self):
        baseline = self.report()
        native = copy.deepcopy(baseline)
        native['sample.0.ink.1.5'] = '2610'
        verify.compare(baseline, native)

    def test_rejects_missing_reports_wrong_fonts_scales_and_unreadable_text(self):
        baseline = self.report()
        for key, value in [('sampleCount', '6'), ('os', ''), ('architecture', 'arm64'),
                           ('outputScaleX', '1.5'), ('sample.0.font', 'Dialog'),
                           ('sample.0.size', '5'), ('sample.0.width', '1'),
                           ('sample.0.height', 'nan'), ('sample.0.ink.2.0', '4'),
                           ('editorTextLength', '0')]:
            with self.subTest(key=key):
                native = copy.deepcopy(baseline)
                native[key] = value
                with self.assertRaises(ValueError):
                    verify.compare(baseline, native)
        with self.assertRaises(ValueError):
            verify.compare({}, {})

    def test_stale_success_report_cannot_mask_a_crash(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            (output / 'fonts.properties').write_text('sampleCount=7')
            with patch.object(verify.subprocess, 'run') as run:
                run.return_value.returncode = 1
                run.return_value.stdout = ''
                run.return_value.stderr = 'crashed'
                with self.assertRaises(RuntimeError):
                    verify.run_check(['Mardexas'], output, 'win')
            self.assertFalse((output / 'fonts.properties').exists())
            self.assertIn('crashed', (output / 'run.log').read_text())


class TargetMetadataTest(unittest.TestCase):
    def test_registers_each_platform_font_backend_and_all_runtime_resources(self):
        for platform, prefixes in metadata.BACKENDS.items():
            with self.subTest(platform=platform), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                with zipfile.ZipFile(directory / 'javafx-graphics-25-test.jar', 'w') as jar:
                    for prefix in prefixes:
                        jar.writestr(prefix + 'Factory.class', b'')
                    jar.writestr('com/sun/prism/shader/FillLoader.class', b'')
                    jar.writestr('com/sun/javafx/font/font.so', b'native')
                    jar.writestr('META-INF/MANIFEST.MF', b'ignored')
                with zipfile.ZipFile(directory / 'RichMarkDownEditor-1.jar', 'w') as jar:
                    jar.writestr('de/richmarkdown/i18n/messages_de.properties', b'translation')
                data = metadata.metadata(directory, platform)
                self.assertEqual(len(prefixes) + 1, len(data['reflection']))
                self.assertTrue(all(kind['jniAccessible'] for kind in data['reflection']))
                self.assertEqual(2, len(data['resources']))
                self.assertIn({'module': 'RichMarkDownEditor', 'glob': 'de/richmarkdown/i18n/messages_de.properties'}, data['resources'])

    def test_empty_or_unknown_runtime_input_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            with self.assertRaises(ValueError):
                metadata.metadata(directory, 'win')
            with zipfile.ZipFile(directory / 'unreviewed.jar', 'w'):
                pass
            with self.assertRaises(ValueError):
                metadata.metadata(directory, 'win')


if __name__ == '__main__':
    unittest.main()
