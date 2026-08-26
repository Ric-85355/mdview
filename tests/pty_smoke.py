#!/usr/bin/env python3
# pty_smoke.py — created 2026-08-26, version 0.2.3.
# Purpose: exercise either mdview implementation in a real UTF-8 pseudo-TTY.
# Algorithm: start the viewer on a controlled terminal, drive panel/search/
# navigation/resize/quit keys, inspect output, and verify terminal restoration.

from __future__ import annotations

import fcntl
import os
import select
import signal
import struct
import subprocess
import sys
import termios
import time
from pathlib import Path


def set_size(descriptor: int, rows: int, columns: int) -> None:
    """Set a pseudo-terminal window size."""
    fcntl.ioctl(descriptor, termios.TIOCSWINSZ, struct.pack("HHHH", rows, columns, 0, 0))


def collect(master: int, duration: float) -> bytes:
    """Collect all terminal output available during a short bounded interval."""
    deadline = time.monotonic() + duration
    chunks: list[bytes] = []
    while time.monotonic() < deadline:
        ready, _, _ = select.select([master], [], [], min(0.05, deadline - time.monotonic()))
        if not ready:
            continue
        try:
            chunks.append(os.read(master, 65536))
        except OSError:
            break
    return b"".join(chunks)


def main() -> int:
    """Run the smoke scenario and fail on rendering or cleanup regressions."""
    if len(sys.argv) != 3:
        raise SystemExit("usage: pty_smoke.py PROGRAM FIXTURE")
    program = str(Path(sys.argv[1]).resolve())
    fixture = str(Path(sys.argv[2]).resolve())
    master, slave = os.openpty()
    set_size(slave, 24, 100)
    before = termios.tcgetattr(slave)
    environment = os.environ.copy()
    environment.update({"TERM": "xterm-256color", "LANG": "C.UTF-8", "LC_ALL": "C.UTF-8"})
    process = subprocess.Popen(
        [program, fixture],
        stdin=slave,
        stdout=slave,
        stderr=slave,
        env=environment,
        close_fds=True,
    )
    output = collect(master, 0.35)
    os.write(master, b"\t")
    output += collect(master, 0.15)
    os.write(master, "/русский\n".encode())
    output += collect(master, 0.25)
    os.write(master, b".")
    set_size(slave, 18, 70)
    process.send_signal(signal.SIGWINCH)
    output += collect(master, 0.25)
    os.write(master, b"\x1b[6~")
    output += collect(master, 0.2)
    os.write(master, b"Q")
    output += collect(master, 0.2)
    status = process.wait(timeout=3)
    after = termios.tcgetattr(slave)
    os.close(master)
    os.close(slave)

    if status != 0:
        raise AssertionError(f"viewer exited with {status}")
    for expected in (
        b"Contents",
        b"Document: parity.md",
        "ГЛАВНЫЙ STRASSE".encode(),
        b"Search: ",
        "русский".encode(),
    ):
        if expected not in output:
            raise AssertionError(f"terminal output lacks {expected!r}")
    if before != after:
        raise AssertionError("terminal attributes were not restored after Q")
    print(f"PTY smoke ({Path(program).name}): OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
