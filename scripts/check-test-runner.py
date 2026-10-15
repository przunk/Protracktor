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


class TestEntryPoint(unittest.TestCase):
    def test_unit_timeout_exits_instead_of_waiting_for_gradle(self):
        with tempfile.TemporaryDirectory(prefix="protracktor-runner-check-") as temporary:
            fixture = Path(temporary)
            scripts = fixture / "scripts"
            scripts.mkdir()
            (scripts / "test-protracktor.sh").write_bytes(
                (ROOT / "scripts/test-protracktor.sh").read_bytes()
            )
            (scripts / "use-tooling.sh").write_text(
                'export PROTRACKTOR_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"\n'
                'export PROTRACKTOR_LOG_PREFIX="$PROTRACKTOR_DIR/check"\n'
                'export PROTRACKTOR_BUILD_DIR_ROOT="$PROTRACKTOR_DIR/build"\n'
                'export ANDROID_HOME="$PROTRACKTOR_DIR/sdk"\n', encoding="utf-8"
            )
            internal = scripts / "internal"
            internal.mkdir()
            runner = ROOT / "scripts/internal/run-check.py"
            if runner.exists():
                (internal / runner.name).write_bytes(runner.read_bytes())
            gradle = fixture / "gradlew"
            gradle.write_text('#!/usr/bin/env bash\necho "fixture: Gradle is stuck"\nsleep 60\n',
                              encoding="utf-8")
            gradle.chmod(0o755)
            environment = dict(os.environ, PROTRACKTOR_UNIT_TEST_TIMEOUT_SECONDS="1",
                               PROTRACKTOR_CHECK_GRACE_SECONDS="0.2")
            started = time.monotonic()
            process = subprocess.Popen(
                ["bash", str(scripts / "test-protracktor.sh")], cwd=fixture,
                env=environment, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, start_new_session=True,
            )
            try:
                try:
                    output, _ = process.communicate(timeout=5)
                except subprocess.TimeoutExpired:
                    self.fail("the test entry point ignored its one-second limit")
                self.assertEqual(124, process.returncode, output)
                self.assertIn("timeout", output.lower())
                self.assertIn("check-test.log", output)
                self.assertLess(time.monotonic() - started, 5)
            finally:
                if process.poll() is None:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.communicate(timeout=5)


if __name__ == "__main__":
    unittest.main()
