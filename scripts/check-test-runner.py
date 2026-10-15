#!/usr/bin/env python3
"""Exercise the real test entry point without Gradle, Android or owner data."""

import os
from pathlib import Path
import signal
import subprocess
import tempfile
import time
import unittest


ROOT = Path(__file__).resolve().parents[1]


class Supervisor(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="protracktor-supervisor-check-")
        self.addCleanup(self.temporary.cleanup)
        self.fixture = Path(self.temporary.name)
        self.log = self.fixture / "run.log"

    def start(self, code, timeout="3", heartbeat="0.2", grace="0.2"):
        return subprocess.Popen(
            ["python3", str(ROOT / "scripts/internal/run-check.py"), "--label", "Fixture",
             "--log", str(self.log), "--timeout", timeout, "--heartbeat", heartbeat,
             "--grace", grace, "--", "python3", "-u", "-c", code],
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, start_new_session=True,
        )

    def finish(self, process):
        try:
            return process.communicate(timeout=6)[0]
        finally:
            if process.poll() is None:
                process.send_signal(signal.SIGTERM)
                try:
                    process.communicate(timeout=2)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.communicate(timeout=2)

    def test_success_keeps_child_output_in_log_not_console(self):
        process = self.start("print('fixture: Żółw ✓')")
        output = self.finish(process)
        self.assertEqual(0, process.returncode, output)
        self.assertNotIn("fixture: Żółw ✓", output)
        self.assertIn("fixture: Żółw ✓", self.log.read_text())
        self.assertIn("finished with exit 0", output)

    def test_successful_verbose_command_has_short_console_and_complete_log(self):
        process = self.start("for i in range(2000): print(f'case {i} PASSED')")
        output = self.finish(process)
        self.assertEqual(0, process.returncode, output)
        self.assertNotIn("PASSED", output)
        self.assertLess(len(output.splitlines()), 8)
        log = self.log.read_text()
        self.assertEqual(2000, log.count("PASSED"))
        self.assertIn("case 0 PASSED", log)
        self.assertIn("case 1999 PASSED", log)

    def test_failure_retains_exit_status_and_diagnostics(self):
        process = self.start("import sys; print('fixture failure'); sys.exit(7)")
        output = self.finish(process)
        self.assertEqual(7, process.returncode, output)
        self.assertIn("fixture failure", output)
        self.assertIn("fixture failure", self.log.read_text())

    def test_failure_shows_bounded_diagnostics_with_full_details_only_in_log(self):
        process = self.start("import sys; [print(f'diagnostic {i}') for i in range(2000)]; sys.exit(7)")
        output = self.finish(process)
        self.assertEqual(7, process.returncode, output)
        self.assertIn("diagnostic 1999", output)
        self.assertNotIn("diagnostic 0\n", output)
        self.assertLessEqual(len(output.splitlines()), 45)
        self.assertEqual(2000, self.log.read_text().count("diagnostic "))

    def test_silent_command_reports_elapsed_time(self):
        process = self.start("import time; time.sleep(.6)")
        output = self.finish(process)
        self.assertEqual(0, process.returncode, output)
        self.assertIn("still running; elapsed", output)

    def worker_tree(self):
        worker = self.fixture / "worker.py"
        worker.write_text(
            "import os, signal, subprocess, sys, time\n"
            "from pathlib import Path\n"
            "signal.signal(signal.SIGTERM, signal.SIG_IGN)\n"
            "Path(sys.argv[1]).write_text(str(os.getpid()))\n"
            "if len(sys.argv) == 3:\n"
            "    subprocess.Popen([sys.executable, __file__, sys.argv[2]], start_new_session=True)\n"
            "while True: time.sleep(.1)\n", encoding="utf-8"
        )
        return (f"import subprocess, sys, time; "
                f"subprocess.Popen([sys.executable, {str(worker)!r}, "
                f"{str(self.fixture / 'child.pid')!r}, {str(self.fixture / 'grandchild.pid')!r}], "
                "start_new_session=True); time.sleep(60)")

    def assert_workers_gone(self):
        for name in ("child.pid", "grandchild.pid"):
            pid_file = self.fixture / name
            self.assertTrue(pid_file.exists(), f"fixture worker did not start: {name}")
            self.assertFalse(Path(f"/proc/{pid_file.read_text()}").exists(),
                             f"owned worker survived: {name}")

    def test_timeout_kills_detached_stubborn_descendants_not_unrelated_processes(self):
        unrelated = subprocess.Popen(["sleep", "60"])
        try:
            process = self.start(self.worker_tree(), timeout=".7")
            output = self.finish(process)
            self.assertEqual(124, process.returncode, output)
            self.assertIn("TIMEOUT", output)
            self.assert_workers_gone()
            self.assertIsNone(unrelated.poll(), "another invocation's process was killed")
        finally:
            unrelated.terminate()
            unrelated.wait(timeout=2)

    def test_ctrl_c_kills_owned_descendants_and_returns_130(self):
        process = self.start(self.worker_tree())
        deadline = time.monotonic() + 2
        while not (self.fixture / "grandchild.pid").exists() and time.monotonic() < deadline:
            time.sleep(.02)
        process.send_signal(signal.SIGINT)
        output = self.finish(process)
        self.assertEqual(130, process.returncode, output)
        self.assertIn("interrupted by SIGINT", output)
        self.assert_workers_gone()

    def test_completed_launcher_does_not_leave_background_workers_or_hold_stdout(self):
        code = self.worker_tree().replace("time.sleep(60)", "time.sleep(.3)")
        process = self.start(code)
        output = self.finish(process)
        self.assertEqual(0, process.returncode, output)
        self.assert_workers_gone()

    def test_ctrl_c_during_post_command_cleanup_cannot_report_success(self):
        marker = self.fixture / "launcher-done"
        code = self.worker_tree().replace("time.sleep(60)", f"time.sleep(.3); open({str(marker)!r}, 'w').close()")
        process = self.start(code, grace=".8")
        deadline = time.monotonic() + 2
        while not marker.exists() and time.monotonic() < deadline:
            time.sleep(.02)
        time.sleep(.2)  # launcher has exited; its TERM-resistant children are being cleaned up
        self.assertIsNone(process.poll(), "fixture did not reach the cleanup window")
        process.send_signal(signal.SIGINT)
        output = self.finish(process)
        self.assert_workers_gone()
        self.assertEqual(130, process.returncode, output)

    def test_invalid_deadline_refuses_to_start_a_command(self):
        for timeout in ("0", "-1", "nan", "inf", "garbage"):
            with self.subTest(timeout=timeout):
                process = self.start("print('SHOULD NOT RUN')", timeout=timeout)
                output = self.finish(process)
                self.assertEqual(2, process.returncode, output)
                self.assertNotIn("SHOULD NOT RUN", output)


class TestEntryPoint(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="protracktor-runner-check-")
        self.addCleanup(self.temporary.cleanup)
        self.fixture = Path(self.temporary.name)
        self.scripts = self.fixture / "scripts"
        self.scripts.mkdir()
        (self.scripts / "test-protracktor.sh").write_bytes(
            (ROOT / "scripts/test-protracktor.sh").read_bytes()
        )
        (self.scripts / "use-tooling.sh").write_text(
            'export PROTRACKTOR_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"\n'
            'export PROTRACKTOR_LOG_PREFIX="$PROTRACKTOR_DIR/check"\n'
            'export PROTRACKTOR_BUILD_DIR_ROOT="$PROTRACKTOR_DIR/build"\n'
            'export ANDROID_HOME="$PROTRACKTOR_DIR/sdk"\n', encoding="utf-8"
        )
        internal = self.scripts / "internal"
        internal.mkdir()
        runner = ROOT / "scripts/internal/run-check.py"
        if runner.exists():
            (internal / runner.name).write_bytes(runner.read_bytes())

    def run_entry(self, gradle_source):
        gradle = self.fixture / "gradlew"
        gradle.write_text("#!/usr/bin/env bash\n" + gradle_source, encoding="utf-8")
        gradle.chmod(0o755)
        environment = dict(os.environ, PROTRACKTOR_UNIT_TEST_TIMEOUT_SECONDS="1",
                           PROTRACKTOR_LINT_TIMEOUT_SECONDS="1", PROTRACKTOR_CHECK_GRACE_SECONDS="0.2",
                           PATH=f"{self.scripts}:{os.environ['PATH']}")
        process = subprocess.Popen(
            ["bash", str(self.scripts / "test-protracktor.sh")], cwd=self.fixture,
            env=environment, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, start_new_session=True,
        )
        try:
            try:
                output, _ = process.communicate(timeout=6)
            except subprocess.TimeoutExpired:
                self.fail("the test entry point ignored its one-second limit")
            return process.returncode, output
        finally:
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGKILL)
                process.communicate(timeout=2)

    def test_unit_timeout_exits_instead_of_waiting_for_gradle(self):
        status, output = self.run_entry('echo "fixture: Gradle is stuck"\nsleep 60\n')
        self.assertEqual(124, status, output)
        self.assertIn("timeout", output.lower())
        self.assertIn("check-test.log", output)

    def test_no_results_and_failed_gradle_do_not_look_successful(self):
        for code, expected in ((0, "no test results"), (7, "Tests failed")):
            with self.subTest(code=code):
                status, output = self.run_entry(f"exit {code}\n")
                self.assertNotEqual(0, status, output)
                self.assertIn(expected, output)
                self.assertIn("check-test.log", output)

    def test_nonpassing_xml_refuses_a_false_gradle_success(self):
        results = self.fixture / "build/app/test-results/testDebugUnitTest"
        results.mkdir(parents=True)
        for field in ("failures", "errors", "skipped"):
            with self.subTest(field=field):
                counts = dict(tests=1, failures=0, errors=0, skipped=0)
                counts[field] = 1
                attributes = " ".join(f'{key}="{value}"' for key, value in counts.items())
                (results / "TEST-fixture.xml").write_text(f"<testsuite {attributes}/>\n")
                status, output = self.run_entry("exit 0\n")
                self.assertEqual(1, status, output)
                self.assertIn("not a complete pass", output)

    def test_lint_deadline_propagates_without_needing_an_error_line(self):
        for locale in ("values", "values-pl"):
            resources = self.fixture / f"app/src/main/res/{locale}"
            resources.mkdir(parents=True)
            (resources / "strings.xml").write_text('<resources><string name="fixture">Fixture</string></resources>\n')
        # Only Gradle and the real outer gate are under test here; auxiliary fixtures stay local.
        for name in ("check-store-metadata.sh", "check-sap-measure.sh", "check-audio-host.sh", "check-release.sh", "node"):
            stub = self.scripts / name
            stub.write_text('#!/usr/bin/env bash\necho "✓ fixture check"\n')
            stub.chmod(0o755)
        (self.scripts / "check-test-runner.py").write_text("print('fixture checks passed')\n")
        status, output = self.run_entry(
            'if [ "$1" = ":app:lintDebug" ]; then sleep 60; exit; fi\n'
            'mkdir -p build/app/test-results/testDebugUnitTest\n'
            'echo \'<testsuite tests="1" failures="0" errors="0" skipped="0"/>\' '
            '> build/app/test-results/testDebugUnitTest/TEST-fixture.xml\n'
        )
        self.assertEqual(124, status, output)
        self.assertIn("[Lint] TIMEOUT", output)
        self.assertIn("check-lint.log", output)


if __name__ == "__main__":
    unittest.main()
