#!/usr/bin/env python3
"""Bound a check with concise status, full file logs and scoped process cleanup (Linux/WSL)."""

import argparse
import ctypes
import math
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import time


def positive_seconds(value):
    number = float(value)
    if not math.isfinite(number) or number <= 0:
        raise argparse.ArgumentTypeError("must be a positive, finite number of seconds")
    return number


def identity(pid):
    try:
        fields = Path(f"/proc/{pid}/stat").read_text().rsplit(")", 1)[1].split()
        return fields[19], fields[0]  # start time guards against signalling a recycled PID
    except (FileNotFoundError, ProcessLookupError):
        return None


def descendants():
    pending = [os.getpid()]
    found = {}
    while pending:
        parent = pending.pop()
        try:
            children = Path(f"/proc/{parent}/task/{parent}/children").read_text().split()
        except (FileNotFoundError, ProcessLookupError):
            continue
        for child in map(int, children):
            current = identity(child)
            if current is not None and child not in found:
                found[child] = current
                pending.append(child)
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--label", required=True)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--timeout", type=positive_seconds, required=True)
    parser.add_argument("--heartbeat", type=positive_seconds, default=30)
    parser.add_argument("--grace", type=positive_seconds,
                        default=os.environ.get("PROTRACKTOR_CHECK_GRACE_SECONDS", "5"))
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        parser.error("a command is required after --")
    # A single-use Gradle daemon may detach from the launcher. Adoption keeps those descendants
    # attributable to this check even after their parent exits; no shared-daemon stop is used.
    if not sys.platform.startswith("linux") or ctypes.CDLL(None, use_errno=True).prctl(36, 1, 0, 0, 0):
        parser.error("Linux/WSL child-subreaper support is required for scoped cleanup")

    interrupted = 0

    def on_signal(number, _frame):
        nonlocal interrupted
        interrupted = number

    for number in (signal.SIGINT, signal.SIGTERM, signal.SIGHUP):
        signal.signal(number, on_signal)

    started = time.monotonic()
    args.log.parent.mkdir(parents=True, exist_ok=True)
    with args.log.open("wb", buffering=0) as log:
        def display(data):
            try:
                sys.stdout.buffer.write(data)
                sys.stdout.buffer.flush()
            except BrokenPipeError:
                on_signal(signal.SIGPIPE, None)

        def emit(data, *, visible=False):
            log.write(data)
            if visible:
                display(data)

        def notice(message):
            emit(f"[{args.label}] {message}\n".encode(), visible=True)

        notice(f"started; timeout {args.timeout:g}s; full log: {args.log}")
        try:
            process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                       start_new_session=True)
        except OSError as error:
            notice(f"could not start: {error}")
            return 127
        selector = selectors.DefaultSelector()
        selector.register(process.stdout, selectors.EVENT_READ)

        def drain(wait=0):
            for key, _ in selector.select(wait):
                data = os.read(key.fileobj.fileno(), 65536)
                if data:
                    emit(data)
                else:
                    selector.unregister(key.fileobj)

        def live_children():
            return {pid: value for pid, value in descendants().items() if value[1] != "Z"}

        def stop_children(number):
            for pid, original in live_children().items():
                current = identity(pid)
                if current is not None and current[0] == original[0]:
                    try:
                        os.kill(pid, number)
                    except ProcessLookupError:
                        pass

        status = None
        heartbeat_at = started + args.heartbeat
        try:
            while process.poll() is None:
                now = time.monotonic()
                if interrupted:
                    status = 128 + interrupted
                    notice(f"interrupted by {signal.Signals(interrupted).name}; stopping this check's processes")
                    break
                if now - started >= args.timeout:
                    status = 124
                    notice(f"TIMEOUT after {now - started:.1f}s; stopping this check's processes")
                    break
                if now >= heartbeat_at:
                    notice(f"still running; elapsed {now - started:.0f}s / {args.timeout:g}s")
                    heartbeat_at = now + args.heartbeat
                drain(min(0.1, max(0, started + args.timeout - now)))
            if status is None:
                status = process.returncode
        finally:
            # Run on success too: a child retaining stdout must not keep a completed gate alive.
            for number in (signal.SIGTERM, signal.SIGKILL):
                deadline = time.monotonic() + args.grace
                while live_children() and time.monotonic() < deadline:
                    stop_children(number)
                    drain(0.05)
                    process.poll()
                if not live_children():
                    break
            remaining = live_children()
            if remaining:
                notice(f"cleanup incomplete for owned PIDs {sorted(remaining)}; refusing success")
                status = 125
            process.poll()
            for pid in descendants():
                if pid != process.pid:
                    try:
                        os.waitpid(pid, os.WNOHANG)
                    except ChildProcessError:
                        pass
            while selector.get_map() and not remaining:
                drain(0.05)
            selector.close()
            process.stdout.close()
        if status is not None and status < 0:
            status = 128 - status
        # The command may already have exited when Ctrl+C arrives during worker cleanup.
        # Completion of that command must not turn an interrupted gate into a success.
        if interrupted and status not in (124, 125):
            status = 128 + interrupted
        if status:
            # Keep the terminal useful without replaying thousands of successful cases on error.
            # Read a bounded tail; the original bytes remain in the complete stage log.
            with args.log.open("rb") as saved:
                saved.seek(0, os.SEEK_END)
                saved.seek(max(0, saved.tell() - 8192))
                excerpt = saved.read().decode("utf-8", errors="replace").splitlines()[-40:]
            notice("last log lines (up to 40 lines / 8 KiB):")
            display(("\n".join(excerpt) + "\n").encode("utf-8"))
        notice(f"finished with exit {status}; elapsed {time.monotonic() - started:.1f}s; full log: {args.log}")
        return 128 + interrupted if interrupted and status not in (124, 125) else status


if __name__ == "__main__":
    sys.exit(main())
