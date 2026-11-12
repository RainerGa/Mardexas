#!/usr/bin/env python3
"""Run the real GUI in an isolated profile and compare native font rendering with its JVM baseline."""
import argparse
import os
import math
import shutil
import signal
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
            if not math.isfinite(expected) or not math.isfinite(actual) or not expected > 0 or not actual > 0 or abs(expected - actual) > tolerance:
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
    log_path = output / "run.log"
    timed_out = False
    # Only Linux wraps Java in xvfb-run and needs a separate process group.
    # Keep macOS GUI processes in the runner's WindowServer login session.
    process_group = os.name != "nt" and platform == "linux"
    with log_path.open("w", encoding="utf-8") as stream:
        process = subprocess.Popen(command, stdout=stream, stderr=subprocess.STDOUT,
                                   env=environment, start_new_session=process_group)
        try:
            code = process.wait(timeout=120)
        except subprocess.TimeoutExpired:
            timed_out = True
            # Kill the complete GUI/display process tree. Killing only xvfb-run
            # leaves Java and Xvfb alive and can make communicate() hang forever.
            if os.name == "nt":
                subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                               stdout=stream, stderr=subprocess.STDOUT, timeout=15, check=False)
            elif process_group:
                os.killpg(process.pid, signal.SIGKILL)
            else:
                process.kill()
            code = process.wait(timeout=15)
    log = log_path.read_text(encoding="utf-8", errors="replace")
    excerpt = log[-4000:]
    for marker in ('MissingReflectionRegistrationError:', 'MissingResourceRegistrationError:',
                   'MissingJNIRegistrationError:'):
        position = log.find(marker)
        if position >= 0:
            excerpt = log[max(0, position - 100):position + 1200] + '\n...\n' + excerpt
            break
    if timed_out or code or "PACKAGE_VERIFICATION_OK" not in log or not report.is_file():
        reason = "timeout" if timed_out else str(code)
        raise RuntimeError(f"GUI verification failed ({reason}); full log: {log_path}\n{excerpt}")
    if any(marker in log for marker in ("MissingReflectionRegistration", "MissingJNIRegistration",
                                        "MissingResourceRegistration", "Exception in thread", "UnsatisfiedLinkError")):
        raise RuntimeError(f"GUI verification reported a native registration error; full log: {log_path}\n{excerpt}")
    return read_report(report)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("trace", "jvm", "native", "portable", "jar", "flatpak"))
    parser.add_argument("--java", default=str(Path(os.environ.get("JAVA_HOME", "")) / "bin/java"))
    parser.add_argument("--input-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--platform", choices=("linux", "win", "mac", "mac-aarch64"), required=True)
    parser.add_argument("--executable", type=Path)
    args = parser.parse_args()
    if args.mode in ("native", "trace") and args.platform not in ("linux", "win"):
        parser.error("GraalVM native and trace modes support only linux or win")
    if args.mode == "flatpak" and (args.platform != "linux" or not args.executable):
        parser.error("flatpak requires --platform linux and --executable pointing to its build directory")
    output = args.output_dir.resolve()
    for renderer in ("default", "sw"):
        if args.mode == "flatpak":
            command = ["flatpak", "build", "--runtime", "--socket=x11", "--unset-env=WAYLAND_DISPLAY", "--env=GDK_BACKEND=x11"]
            if renderer == "sw":
                command.append("--env=JAVA_TOOL_OPTIONS=-Dprism.order=sw")
            command.extend([args.executable.resolve(), "/app/bin/mardexas"])
            report = run_check(command, output / ("flatpak-" + renderer), args.platform)
            # Sandbox fonts may differ from the host. Validate completeness and
            # positive finite metrics/pixel counts without comparing font names
            # against an unrelated host font installation.
            compare(report, report)
            continue
        baseline = output / ("jvm-" + renderer)
        trace = output / ("trace-" + renderer)
        if args.mode in ("trace", "jvm"):
            agent = []
            if args.mode == "trace":
                if trace.exists():
                    shutil.rmtree(trace)
                trace.mkdir(parents=True, exist_ok=True)
                agent = [f"-agentlib:native-image-agent=config-output-dir={trace}"]
            options = [] if renderer == "default" else ["-Dprism.order=sw"]
            run_check([args.java, *agent,
                       "-Dprism.debugfonts=true", *options,
                       "--module-path", args.input_dir.resolve(),
                       "--add-exports=com.gluonhq.richtextarea/com.gluonhq.richtextarea.viewmodel=RichMarkDownEditor",
                       "--module", "RichMarkDownEditor/de.richmarkdown.Launcher"], baseline, args.platform)
        else:
            if not args.executable:
                parser.error("--executable is required for package verification")
            options = [] if renderer == "default" else ["-Dprism.order=sw"]
            environment = None
            if args.mode == 'native':
                command = [args.executable.resolve(), "-XX:MissingRegistrationReportingMode=Exit",
                           "-Dprism.debugfonts=true", *options]
            elif args.mode == 'jar':
                command = [args.java, '-Dprism.debugfonts=true', *options,
                           '--enable-native-access=ALL-UNNAMED', '-jar', args.executable.resolve()]
            else:
                # jpackage launchers forward command-line -D options to main(),
                # so supply VM options using the VM's supported environment hook.
                environment = dict(os.environ)
                environment['JAVA_TOOL_OPTIONS'] = ' '.join([
                    environment.get('JAVA_TOOL_OPTIONS', ''), '-Dprism.debugfonts=true', *options])
                command = [args.executable.resolve()]
            native = run_check(command, output / (args.mode + "-" + renderer), args.platform, environment)
            compare(read_report(baseline / "fonts.properties"), native)
    print(f"Package {args.mode} checks passed: {output}")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, RuntimeError, subprocess.TimeoutExpired) as error:
        print(error, file=sys.stderr)
        sys.exit(1)
