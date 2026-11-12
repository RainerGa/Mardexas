import copy
import hashlib
import os
import shutil
import importlib.util
from pathlib import Path
import tempfile
import subprocess
import struct
import tarfile
import unittest
from unittest.mock import Mock, patch
from types import SimpleNamespace
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
                           ('sample.0.height', 'nan'), ('sample.0.height', 'inf'), ('sample.0.ink.2.0', '4'),
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
            with patch.object(verify.subprocess, 'Popen') as run:
                run.return_value.wait.return_value = 1
                with self.assertRaises(RuntimeError):
                    verify.run_check(['Mardexas'], output, 'win')
            self.assertFalse((output / 'fonts.properties').exists())
            self.assertTrue((output / 'run.log').is_file())

    def test_timeout_kills_the_entire_unix_gui_process_group(self):
        with tempfile.TemporaryDirectory() as temporary:
            # Do not mutate global os.name: pathlib would create unsupported
            # PosixPaths on a Windows host. Simulate just the process adapter.
            kill = Mock()
            with patch.object(verify, 'os', SimpleNamespace(name='posix', killpg=kill)), patch.object(verify, 'signal', SimpleNamespace(SIGKILL=9)), patch.object(verify.subprocess, 'Popen') as run:
                run.return_value.pid = 456
                run.return_value.wait.side_effect = [verify.subprocess.TimeoutExpired('GUI', 120), -9]
                with self.assertRaisesRegex(RuntimeError, 'timeout'):
                    verify.run_check(['Mardexas'], Path(temporary), 'linux')
                kill.assert_called_once_with(456, 9)

    def test_timeout_kills_the_entire_windows_gui_process_tree(self):
        with tempfile.TemporaryDirectory() as temporary:
            with patch.object(verify, 'os', SimpleNamespace(name='nt')), patch.object(verify.subprocess, 'Popen') as run, patch.object(verify.subprocess, 'run') as taskkill:
                run.return_value.pid = 789
                run.return_value.wait.side_effect = [verify.subprocess.TimeoutExpired('GUI', 120), -9]
                with self.assertRaisesRegex(RuntimeError, 'timeout'):
                    verify.run_check(['Mardexas'], Path(temporary), 'win')
                self.assertFalse(run.call_args.kwargs['start_new_session'])
                self.assertEqual(['taskkill', '/PID', '789', '/T', '/F'], taskkill.call_args.args[0])

    def test_all_package_modes_check_both_renderers_with_the_correct_launcher(self):
        for mode in ('trace', 'jvm', 'native', 'portable', 'jar'):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                report = self.report()
                for renderer in ('default', 'sw'):
                    baseline = root / ('jvm-' + renderer)
                    baseline.mkdir()
                    (baseline / 'fonts.properties').write_text(''.join(f'{k}={v}\n' for k, v in report.items()))
                arguments = SimpleNamespace(mode=mode, java='java', input_dir=root,
                                            output_dir=root, platform='win', executable=root / 'Mardexas')
                with patch.object(verify.argparse.ArgumentParser, 'parse_args', return_value=arguments), patch.object(verify, 'run_check', return_value=report) as run:
                    verify.main()
                self.assertEqual(2, run.call_count)
                commands = [call.args[0] for call in run.call_args_list]
                if mode == 'trace':
                    self.assertTrue(any('native-image-agent' in str(arg) for arg in commands[0]))
                if mode == 'jvm':
                    self.assertFalse(any('native-image-agent' in str(arg) for arg in commands[0]))
                if mode == 'native':
                    self.assertIn('-XX:MissingRegistrationReportingMode=Exit', commands[0])
                if mode == 'jar':
                    self.assertIn('-jar', commands[0])
                if mode == 'portable':
                    self.assertEqual([root / 'Mardexas'], commands[0])
                    self.assertIn('-Dprism.order=sw', run.call_args_list[1].args[3]['JAVA_TOOL_OPTIONS'])
                else:
                    self.assertIn('-Dprism.order=sw', commands[1])


    def test_flatpak_uses_platform_runtime_and_rejects_unreadable_or_incomplete_reports(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            arguments = SimpleNamespace(mode='flatpak', java='java', input_dir=root,
                                        output_dir=root, platform='linux', executable=root / 'flatpak-build')
            with patch.object(verify.argparse.ArgumentParser, 'parse_args', return_value=arguments):
                with patch.object(verify, 'run_check', return_value=self.report()) as run:
                    verify.main()
                self.assertEqual(2, run.call_count)
                for call in run.call_args_list:
                    self.assertIn('--runtime', call.args[0])
                    self.assertEqual('/app/bin/mardexas', call.args[0][-1])
                self.assertIn('--env=JAVA_TOOL_OPTIONS=-Dprism.order=sw', run.call_args_list[1].args[0])
                for report in ({}, dict(self.report(), **{'sample.0.ink.1.0': '0'})):
                    with patch.object(verify, 'run_check', return_value=report), self.assertRaises((ValueError, KeyError)):
                        verify.main()
                arguments.platform = 'win'
                with self.assertRaises(SystemExit): verify.main()


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
                    jar.writestr('de/richmarkdown/ui/app.css', b'.root {}')
                data = metadata.metadata(directory, platform)
                self.assertEqual(len(prefixes) + 1 + len(metadata.SEGMENT_TYPES), len(data['reflection']))
                self.assertTrue(all(kind['jniAccessible'] for kind in data['reflection'] if not kind['type'].startswith('java.')))
                self.assertIn({'type': 'java.lang.invoke.VarHandleSegmentAsIntsAligned', 'allDeclaredMethods': True}, data['reflection'])
                self.assertEqual(6, len(data['resources']))
                self.assertIn({'module': 'RichMarkDownEditor', 'glob': 'de/richmarkdown/ui/app.bss'}, data['resources'])
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


class RuntimeNoticesTest(unittest.TestCase):
    def test_original_javafx_notices_for_all_targets_are_complete_and_hash_verified(self):
        notices = load('copy-runtime-notices')
        project = Path(__file__).resolve().parent.parent
        with tempfile.TemporaryDirectory() as temporary:
            for platform in ('linux', 'win', 'mac', 'mac-aarch64'):
                target = Path(temporary) / platform
                notices.copy_javafx_notices(project, target, platform)
                for module in ('javafx.base', 'javafx.controls', 'javafx.graphics'):
                    license_file = target / f'javafx-25.0.4-licenses/{module}/LICENSE'
                    self.assertIn('GNU GENERAL PUBLIC LICENSE', license_file.read_text().upper())

    def test_modified_or_incomplete_javafx_notices_fail(self):
        notices = load('copy-runtime-notices')
        project = Path(__file__).resolve().parent.parent
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            shutil.copytree(project / 'packaging/licenses', root / 'packaging/licenses')
            (root / 'editor').mkdir()
            shutil.copyfile(project / 'editor/pom.xml', root / 'editor/pom.xml')
            license_file = root / 'packaging/licenses/javafx-25.0.4/linux/javafx.base/LICENSE'
            license_file.write_text('modified license')
            with self.assertRaisesRegex(ValueError, 'differs from upstream'):
                notices.copy_javafx_notices(root, root / 'output', 'linux')
            origin_file = root / 'packaging/licenses/javafx-25.0.4/ORIGIN.json'
            import json
            origin = json.loads(origin_file.read_text())
            del origin['targets']['linux']['files']['javafx.base/LICENSE']
            origin_file.write_text(json.dumps(origin))
            with self.assertRaisesRegex(ValueError, 'Missing JavaFX legal notice'):
                notices.copy_javafx_notices(root, root / 'output', 'linux')

    def test_copies_exact_vendor_notices_and_resolves_internal_symlinks(self):
        notices = load('copy-runtime-notices')
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            java = root / 'jdk'
            (java / 'legal/java.base').mkdir(parents=True)
            (java / 'legal/java.base/LICENSE').write_text('Original vendor license')
            (java / 'legal/java.base/NOTICE').write_text('Copyright notice')
            (java / 'legal/java.logging').mkdir()
            try:
                (java / 'legal/java.logging/LICENSE').symlink_to('../java.base/LICENSE')
            except OSError:
                # Windows runners can lack symbolic-link creation privileges.
                (java / 'legal/java.logging/LICENSE').write_text('Original vendor license')
            (java / 'legal/java.base/LICENSE').chmod(0o444)
            (java / 'release').write_text('JAVA_VERSION="25.0.2"')
            notices.copy_notices(java, root / 'package')
            self.assertEqual('Original vendor license', (root / 'package/runtime-licenses/java.base/LICENSE').read_text())
            self.assertEqual('JAVA_VERSION="25.0.2"', (root / 'package/runtime-licenses/BUILD-RUNTIME.properties').read_text())
            copied = root / 'package/runtime-licenses/java.logging/LICENSE'
            self.assertFalse(copied.is_symlink())
            self.assertEqual('Original vendor license', copied.read_text())
            notices.copy_notices(java, root / 'package')
            self.assertEqual('Original vendor license', copied.read_text())
            with self.assertRaises(ValueError):
                notices.copy_notices(root / 'missing', root / 'bad-package')


class SourceArchiveTest(unittest.TestCase):
    def git(self, root, *arguments):
        subprocess.run(['git', '-C', str(root), *arguments], check=True,
                       capture_output=True)

    def test_actual_dirty_sources_unicode_and_initialized_submodule_are_archived(self):
        source = load('source-archive')
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / 'project'
            root.mkdir()
            self.git(root, 'init')
            (root / '.gitignore').write_text('target/\n')
            (root / 'tracked.txt').write_text('old')
            (root / 'deleted.txt').write_text('deleted')
            self.git(root, 'add', '.')
            (root / 'tracked.txt').write_text('actual compiled source')
            (root / 'deleted.txt').unlink()
            (root / 'Übersicht mit Leerzeichen.md').write_text('new')
            (root / 'target').mkdir()
            (root / 'target/secret').write_text('ignored')
            child = root / 'fork'
            child.mkdir()
            self.git(child, 'init')
            (child / 'library.java').write_text('actual fork source')
            self.git(root, 'update-index', '--add', '--cacheinfo', '160000,' + '1' * 40 + ',fork')
            destination = Path(temporary) / 'source.tar.gz'
            self.assertEqual(4, source.create_archive(root, destination))
            with tarfile.open(destination) as archive:
                names = archive.getnames()
                self.assertIn('Mardexas/Übersicht mit Leerzeichen.md', names)
                self.assertIn('Mardexas/fork/library.java', names)
                self.assertNotIn('Mardexas/target/secret', names)
                self.assertNotIn('Mardexas/deleted.txt', names)
                self.assertEqual(b'actual compiled source', archive.extractfile('Mardexas/tracked.txt').read())
                self.assertFalse(any('/.git/' in name for name in names))

    def test_missing_submodule_and_empty_source_fail(self):
        source = load('source-archive')
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.git(root, 'init')
            with self.assertRaisesRegex(ValueError, 'No application sources'):
                source.create_archive(root, root.parent / 'empty.tar.gz')
            self.git(root, 'update-index', '--add', '--cacheinfo', '160000,' + '1' * 40 + ',fork')
            with self.assertRaisesRegex(ValueError, 'Uninitialized'):
                list(source.source_files(root))


class FatJarArchitectureTest(unittest.TestCase):
    def library(self, platform):
        header = bytearray(128)
        if platform == 'linux':
            header[:6] = b'\x7fELF\x02\x01'
            struct.pack_into('<H', header, 18, 62)
            return 'libglass.so', header
        if platform == 'win':
            header[:2] = b'MZ'
            struct.pack_into('<I', header, 60, 64)
            header[64:68] = b'PE\0\0'
            struct.pack_into('<H', header, 68, 0x8664)
            return 'glass.dll', header
        header[:4] = b'\xcf\xfa\xed\xfe'
        struct.pack_into('<I', header, 4, 0x1000007 if platform == 'mac' else 0x100000c)
        return 'libglass.dylib', header

    def test_all_four_targets_accept_their_libraries_and_reject_other_targets(self):
        checker = load('verify-fat-jar')
        platforms = ('linux', 'win', 'mac', 'mac-aarch64')
        with tempfile.TemporaryDirectory() as temporary:
            jar = Path(temporary) / 'editor.jar'
            for platform in platforms:
                with zipfile.ZipFile(jar, 'w') as archive:
                    archive.writestr(*self.library(platform))
                self.assertEqual(1, checker.verify(platform, jar))
                for wrong in set(platforms) - {platform}:
                    with self.subTest(source=platform, target=wrong), self.assertRaises(ValueError):
                        checker.verify(wrong, jar)

    def test_missing_libraries_corrupt_headers_and_mixed_targets_fail(self):
        checker = load('verify-fat-jar')
        with tempfile.TemporaryDirectory() as temporary:
            jar = Path(temporary) / 'editor.jar'
            for entries in ([], [('libglass.so', b'invalid')],
                            [self.library('linux'), self.library('win')]):
                with zipfile.ZipFile(jar, 'w') as archive:
                    for entry in entries:
                        archive.writestr(*entry)
                with self.assertRaises(ValueError):
                    checker.verify('linux', jar)


class GraalVmTargetsTest(unittest.TestCase):
    def test_removed_macos_targets_fail_before_building_or_tracing(self):
        packaging = Path(__file__).resolve().parent
        for platform in ('mac', 'mac-aarch64'):
            commands = [
                ['bash', str(packaging / 'build-native-image.sh')],
                ['bash', str(packaging / 'trace-reachability.sh'), platform, 'all'],
            ]
            for command in commands:
                with self.subTest(platform=platform, script=command[1]):
                    result = subprocess.run(command, env={**os.environ, 'JAVAFX_PLATFORM': platform},
                                            capture_output=True, text=True, timeout=10)
                    self.assertEqual(2, result.returncode, result.stderr)
                    self.assertIn('expected linux or win', result.stderr)
            with self.assertRaisesRegex(ValueError, 'Unsupported GraalVM platform'):
                metadata.metadata(packaging, platform)

    def test_macos_native_verification_and_agent_tracing_are_rejected(self):
        for platform in ('mac', 'mac-aarch64'):
            for mode in ('native', 'trace'):
                with self.subTest(platform=platform, mode=mode), \
                        patch('sys.argv', ['verify-package.py', mode, '--platform', platform,
                                           '--input-dir', '.', '--output-dir', '.']), \
                        patch.object(verify, 'run_check') as run, \
                        patch('sys.stderr'), self.assertRaises(SystemExit) as error:
                    verify.main()
                self.assertEqual(2, error.exception.code)
                run.assert_not_called()


class ReleaseInventoryTest(unittest.TestCase):
    def test_native_assets_are_linux_and_windows_only_and_macos_jvm_packages_remain(self):
        names = load('release-inventory').expected_assets('1.2.3')
        self.assertEqual({
            'Mardexas-1.2.3-linux-x86_64-native.tar.gz',
            'Mardexas-1.2.3-windows-x86_64-native.tar.gz',
        }, {name for name in names if name.endswith('-native.tar.gz')})
        for architecture in ('x86_64', 'aarch64'):
            for suffix in ('-portable.zip', '.dmg', '.pkg', '-fat.jar'):
                self.assertIn(f'Mardexas-1.2.3-macos-{architecture}{suffix}', names)

    def test_complete_release_has_reproducible_checksums(self):
        checker = load('release-inventory')
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            names = checker.expected_assets('1.2.3')
            self.assertEqual(21, len(names))
            for name in names:
                (root / name).write_bytes(name.encode())
            self.assertEqual(21, checker.inventory(root, '1.2.3'))
            before = (root / 'SHA256SUMS').read_text()
            for line in before.splitlines():
                digest, name = line.split('  ')
                self.assertEqual(hashlib.sha256(name.encode()).hexdigest(), digest)
            checker.inventory(root, '1.2.3')
            self.assertEqual(before, (root / 'SHA256SUMS').read_text())

    def test_every_missing_asset_empty_asset_and_unexpected_version_fails(self):
        checker = load('release-inventory')
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            names = checker.expected_assets('1.0.0')
            for name in names:
                (root / name).write_bytes(b'package')
            for name in names:
                (root / name).unlink()
                with self.subTest(missing=name), self.assertRaises(ValueError):
                    checker.inventory(root, '1.0.0')
                (root / name).write_bytes(b'')
                with self.subTest(empty=name), self.assertRaises(ValueError):
                    checker.inventory(root, '1.0.0')
                (root / name).write_bytes(b'package')
            (root / 'Mardexas-0.9.0-stale.jar').write_bytes(b'stale')
            with self.assertRaises(ValueError):
                checker.inventory(root, '1.0.0')
        with self.assertRaises(ValueError):
            checker.expected_assets('../malformed')


@unittest.skipUnless(os.name == 'posix', 'Bash tool harness needs a POSIX host')
class InstallerScriptTest(unittest.TestCase):
    def setup_project(self, root):
        (root / 'packaging').mkdir()
        shutil.copyfile(Path(__file__).with_name('build-installers.sh'), root / 'packaging/build-installers.sh')
        (root / 'editor').mkdir()
        (root / 'editor/pom.xml').write_text('<project>\n  <version>1.2.3</version>\n</project>')
        (root / 'LICENSE').write_text('Original application license')
        (root / 'dist/linux/Mardexas').mkdir(parents=True)
        tool = root / 'jdk/bin/jpackage'
        tool.parent.mkdir(parents=True)
        tool.write_text('''#!/usr/bin/env python3
import pathlib, sys
args=sys.argv[1:]
output=pathlib.Path(args[args.index('--dest')+1])
kind=args[args.index('--type')+1]
assert pathlib.Path(args[args.index('--app-image')+1]).is_dir()
assert pathlib.Path(args[args.index('--license-file')+1]).read_text() == 'Original application license'
(output / ('mardexas.'+kind)).write_bytes(b'packaged app image')
''')
        tool.chmod(0o755)
        return dict(os.environ, JAVA_HOME=str(root / 'jdk'), JAVAFX_PLATFORM='linux')

    def test_both_default_installers_or_one_requested_type_are_exported(self):
        for arguments, extensions in (([], ('deb', 'rpm')), (['rpm'], ('rpm',))):
            with self.subTest(arguments=arguments), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                env = self.setup_project(root)
                result = subprocess.run(['bash', str(root / 'packaging/build-installers.sh'), *arguments], env=env, capture_output=True)
                self.assertEqual(0, result.returncode, result.stderr)
                for extension in extensions:
                    self.assertEqual(b'packaged app image', (root / f'dist/Mardexas-1.2.3-linux-x86_64.{extension}').read_bytes())

    def test_invalid_type_and_missing_application_image_fail(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            env = self.setup_project(root)
            for arguments in (['msi'], ['rpm', 'deb']):
                result = subprocess.run(['bash', str(root / 'packaging/build-installers.sh'), *arguments], env=env, capture_output=True)
                self.assertEqual(2, result.returncode)
            (root / 'dist/linux/Mardexas').rmdir()
            result = subprocess.run(['bash', str(root / 'packaging/build-installers.sh')], env=env, capture_output=True)
            self.assertEqual(2, result.returncode)


if __name__ == '__main__':
    unittest.main()
