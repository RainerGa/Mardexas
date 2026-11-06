#!/usr/bin/env python3
"""Run the real GUI in an isolated profile and compare native font rendering with its JVM baseline."""
import argparse
import os
from pathlib import Path
import subprocess
import sys


def read_report(path):
    values = {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if line and not line.startswith(("#", "!")) and "=" in line:
            key, value = line.split("=", 1)
            values[key] = value
    return values


def compare(baseline, native):
    if not baseline or baseline.get("sampleCount") != native.get("sampleCount"):
        raise ValueError("Font samples are missing or their counts differ")
    count = int(baseline["sampleCount"])
    if count < 7:
        raise ValueError("Incomplete regular/bold/italic/monospace/heading coverage")
    for key in ("os", "architecture", "outputScaleX", "outputScaleY"):
        if not baseline.get(key) or baseline.get(key) != native.get(key):
            raise ValueError(f"Baseline and native must use the same environment: {key}")
    for index in range(count):
        prefix = f"sample.{index}."
        if not baseline.get(prefix + "font") or baseline.get(prefix + "font") != native.get(prefix + "font"):
            raise ValueError(f"Different font selected for sample {index}")
        for metric in ("size", "width", "height", "ink.1.0", "ink.1.5", "ink.2.0"):
            key = prefix + metric
            expected, actual = float(baseline[key]), float(native[key])
            # Same OS, same JavaFX version and same renderer: metrics should be
            # effectively identical. Permit minor rasterization differences.
            tolerance = max(2, expected * .08) if metric.startswith("ink.") else .5
            if not expected > 0 or not actual > 0 or abs(expected - actual) > tolerance:
                raise ValueError(f"Native font rendering differs: {key}: JVM={expected}, native={actual}")
    if int(native.get("editorTextLength", "0")) < 40:
        raise ValueError("The actual editor did not load its sample document")


def run_check(command, output, platform, environment=None):
    output = Path(output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    report = output / "fonts.properties"
    report.unlink(missing_ok=True)  # A stale successful report must never pass.
    command = [str(item) for item in command] + ["--verify-package", str(output)]
    if platform == "linux":
        command = ["xvfb-run", "-a", "--server-args=-screen 0 1600x1200x24"] + command
    result = subprocess.run(command, capture_output=True, text=True, timeout=120, env=environment)
    log = result.stdout + result.stderr
    (output / "run.log").write_text(log, encoding="utf-8")
    if result.returncode or "PACKAGE_VERIFICATION_OK" not in result.stdout or not report.is_file():
        raise RuntimeError(f"GUI verification failed ({result.returncode}):\n{log}")
    if any(marker in log for marker in ("MissingReflectionRegistration", "MissingJNIRegistration",
                                        "MissingResourceRegistration", "Exception in thread", "UnsatisfiedLinkError")):
        raise RuntimeError(f"GUI verification reported a native registration error:\n{log}")
    return read_report(report)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("trace", "native"))
    parser.add_argument("--java", default=str(Path(os.environ.get("JAVA_HOME", "")) / "bin/java"))
    parser.add_argument("--input-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--platform", choices=("linux", "win", "mac", "mac-aarch64"), required=True)
    parser.add_argument("--executable", type=Path)
    args = parser.parse_args()
    output = args.output_dir.resolve()
    for renderer in ("default", "sw"):
        baseline = output / ("jvm-" + renderer)
        trace = output / ("trace-" + renderer)
        if args.mode == "trace":
            trace.mkdir(parents=True, exist_ok=True)
            options = [] if renderer == "default" else ["-Dprism.order=sw"]
            run_check([args.java, f"-agentlib:native-image-agent=config-output-dir={trace}",
                       "-Dprism.debugfonts=true", *options,
                       "--module-path", args.input_dir.resolve(),
                       "--add-exports=com.gluonhq.richtextarea/com.gluonhq.richtextarea.viewmodel=RichMarkDownEditor",
                       "--module", "RichMarkDownEditor/de.richmarkdown.Launcher"], baseline, args.platform)
        else:
            if not args.executable:
                parser.error("--executable is required for native verification")
            # Native Image accepts Java -D arguments at run time too.
            options = [] if renderer == "default" else ["-Dprism.order=sw"]
            native = run_check([args.executable.resolve(), "-XX:MissingRegistrationReportingMode=Exit",
                                "-Dprism.debugfonts=true", *options], output / ("native-" + renderer), args.platform)
            compare(read_report(baseline / "fonts.properties"), native)
    print(f"Package {args.mode} checks passed: {output}")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, RuntimeError, subprocess.TimeoutExpired) as error:
        print(error, file=sys.stderr)
        sys.exit(1)
