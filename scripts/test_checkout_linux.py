"""Exercise the bootstrap against real, offline Git repositories on Linux."""

import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("checkout-linux.sh").resolve()


class CheckoutTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="mardexas checkout ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.env = dict(os.environ, GIT_ALLOW_PROTOCOL="file",
                        GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull,
                        GIT_TERMINAL_PROMPT="0")
        self.child = self.root / "dependency"
        self.repo = self.root / "source"
        for repo in (self.child, self.repo):
            repo.mkdir()
            self.git(repo, "init", "--initial-branch=main")
        (self.child / "pinned.txt").write_text("pinned dependency\n")
        self.commit(self.child)
        self.pinned = self.git(self.child, "rev-parse", "HEAD").stdout.strip()
        (self.repo / "pom.xml").write_text("<project/>\n")
        self.git(self.repo, "submodule", "add", str(self.child), "rich-text-area")
        self.commit(self.repo)
        self.git(self.repo, "tag", "v-test")
        self.git(self.repo, "switch", "-c", "feature/test")
        (self.repo / "feature.txt").write_text("feature branch\n")
        self.commit(self.repo)
        self.git(self.repo, "switch", "main")
        (self.child / "pinned.txt").write_text("newer unpublished dependency\n")
        self.commit(self.child)
        self.destination = self.root / "destination with spaces"

    def git(self, repo, *args):
        return subprocess.run(["git", "-C", str(repo), *args], env=self.env,
                              text=True, capture_output=True, check=True)

    def commit(self, repo):
        self.git(repo, "add", "--all")
        self.git(repo, "-c", "user.name=Checkout Test", "-c",
                 "user.email=checkout@example.invalid", "-c", "commit.gpgsign=false",
                 "commit", "-m", "fixture")

    def run_script(self, *args, env=None):
        return subprocess.run(["bash", str(SCRIPT), *map(str, args)],
                              cwd=self.root, env=env or self.env,
                              text=True, capture_output=True)

    def clone(self, *args, env=None):
        return self.run_script("--repository", self.repo, *args,
                               self.destination, env=env)

    def test_complete_checkout_uses_pinned_dependency(self):
        result = self.clone()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue((self.destination / "pom.xml").exists())
        self.assertTrue((self.destination / ".git").is_dir())
        self.assertEqual(self.git(self.destination / "rich-text-area",
                                  "rev-parse", "HEAD").stdout.strip(), self.pinned)
        self.assertFalse((self.destination / "feature.txt").exists())
        self.assertIn("docs/DEVELOPMENT.md", result.stdout)

    def test_branch_and_tag_selection(self):
        for ref, feature in (("feature/test", True), ("v-test", False)):
            with self.subTest(ref=ref):
                self.destination = self.root / ref.replace("/", "-")
                result = self.clone("--ref", ref)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual((self.destination / "feature.txt").exists(), feature)

    def test_existing_destination_preserved(self):
        self.destination.mkdir()
        marker = self.destination / "my-work.txt"
        marker.write_text("do not overwrite")
        result = self.clone()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(marker.read_text(), "do not overwrite")
        self.assertFalse((self.destination / ".git").exists())

    def test_broken_symlink_destination_preserved(self):
        self.destination.symlink_to(self.root / "absent")
        self.assertNotEqual(self.clone().returncode, 0)
        self.assertTrue(self.destination.is_symlink())

    def test_bad_arguments_fail_before_checkout(self):
        for args in (("--ref",), ("--repository",), ("--unknown",),
                     ("one", "two"), ("--",)):
            with self.subTest(args=args):
                self.assertNotEqual(self.run_script(*args).returncode, 0)

    def test_help(self):
        result = self.run_script("--help")
        self.assertEqual(result.returncode, 0)
        self.assertIn("--verify", result.stdout)

    def test_missing_remote_ref_fails(self):
        result = self.clone("--ref", "absent-branch")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Checkout failed", result.stderr)

    def test_unavailable_submodule_fails_and_preserves_parent_sources(self):
        self.git(self.repo, "config", "-f", ".gitmodules",
                 "submodule.rich-text-area.url", str(self.root / "missing-repo"))
        self.commit(self.repo)
        result = self.clone()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Checkout failed", result.stderr)
        self.assertTrue((self.destination / "pom.xml").exists())

    def test_default_destination(self):
        result = self.run_script("--repository", self.repo)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue((self.root / "Mardexas" / "pom.xml").exists())

    def fake_tools(self, version="25.0.2", exit_code=0):
        home = self.root / "jdk"
        bin_dir = home / "bin"
        bin_dir.mkdir(parents=True)
        javac = bin_dir / "javac"
        javac.write_text(f"#!/bin/sh\nprintf 'javac {version}\\n'\n")
        javac.chmod(0o755)
        maven = self.root / "maven with spaces"
        maven.write_text("#!/usr/bin/env python3\nimport json, os, sys\n"
                         "from pathlib import Path\n"
                         "Path(os.environ['CHECKOUT_TEST_LOG']).write_text("
                         "json.dumps({'cwd': os.getcwd(), 'args': sys.argv[1:]}))\n"
                         f"sys.exit({exit_code})\n")
        maven.chmod(0o755)
        log = self.root / "maven.json"
        return dict(self.env, JAVA_HOME=str(home), MVN=str(maven),
                    CHECKOUT_TEST_LOG=str(log)), log

    def test_verify_runs_in_checkout_with_separate_module_outputs(self):
        env, log = self.fake_tools()
        result = self.clone("--verify", env=env)
        self.assertEqual(result.returncode, 0, result.stderr)
        call = json.loads(log.read_text())
        self.assertEqual(call["cwd"], str(self.destination))
        self.assertIn("verify", call["args"])
        self.assertIn("-Dmardexas.build.directory=${project.basedir}/target/checkout-verification",
                      call["args"])

    def test_wrong_jdk_fails_before_checkout(self):
        env, log = self.fake_tools(version="27-ea")
        result = self.clone("--verify", env=env)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("JDK 25 required", result.stderr)
        self.assertFalse(self.destination.exists())
        self.assertFalse(log.exists())

    def test_failed_build_preserves_sources_and_exit_status(self):
        env, _ = self.fake_tools(exit_code=17)
        result = self.clone("--verify", env=env)
        self.assertEqual(result.returncode, 17)
        self.assertTrue((self.destination / "pom.xml").exists())
        self.assertNotIn("Next steps", result.stdout)


if __name__ == "__main__":
    unittest.main()
