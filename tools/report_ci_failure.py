#!/usr/bin/env python3
"""Turn a failed Gradle run into GitHub Actions check annotations.

Runner logs are not always reachable (for example when CI is watched from a sandbox with
restricted network access), but check annotations are part of the plain REST API. So after
a failing build this script re-emits the interesting lines — Kotlin/Java compiler errors,
the Gradle "what went wrong" block and every failed test — as `::error::` annotations:

    ./gradlew testDebugUnitTest 2>&1 | tee gradle.log
    python3 tools/report_ci_failure.py gradle.log

Usage: report_ci_failure.py [logfile ...]
"""
from __future__ import annotations

import glob
import re
import sys
import xml.etree.ElementTree as ET

MAX_ANNOTATIONS = 40
MAX_MESSAGE = 400


def escape(text: str) -> str:
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def emit(message: str) -> None:
    message = " ".join(message.split())[:MAX_MESSAGE]
    if message:
        print(f"::error::{escape(message)}")


def compiler_errors(lines: list[str]) -> list[str]:
    """Kotlin (`e: file:line:col: msg`) and Java/aapt (`path:line: error: msg`) errors."""
    found = []
    for line in lines:
        if line.startswith("e: ") or line.startswith("w: error"):
            found.append(line)
        elif re.search(r"(error:|FAILED$|FAILURE:|What went wrong|Execution failed)", line):
            found.append(line)
    return found


def gradle_cause(lines: list[str]) -> list[str]:
    if "* What went wrong:" not in lines:
        return []
    start = lines.index("* What went wrong:")
    end = next((i for i in range(start, len(lines)) if lines[i].startswith("* Try:")), len(lines))
    return lines[start : min(end + 3, len(lines))]


def failed_tests() -> list[str]:
    out = []
    for path in sorted(glob.glob("app/build/test-results/**/*.xml", recursive=True)):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for case in root.iter("testcase"):
            for problem in list(case.iter("failure")) + list(case.iter("error")):
                message = (problem.get("message") or problem.text or "").strip()
                first = next((ln for ln in message.splitlines() if ln.strip()), "no message")
                out.append(f'{case.get("classname")}.{case.get("name")}: {first}')
    return out


def main(argv: list[str]) -> int:
    logs = argv[1:] or ["gradle.log"]
    lines: list[str] = []
    for log in logs:
        try:
            with open(log, encoding="utf-8", errors="replace") as handle:
                lines += handle.read().splitlines()
        except OSError:
            print(f"note: {log} not found")

    problems = failed_tests() + compiler_errors(lines) + gradle_cause(lines)

    seen: set[str] = set()
    unique = [p for p in problems if not (p in seen or seen.add(p))]
    for problem in unique[:MAX_ANNOTATIONS]:
        emit(problem)

    if not unique and lines:
        emit("gradle failed with no recognisable error line; tail: " + " | ".join(lines[-25:]))

    print(f"reported {min(len(unique), MAX_ANNOTATIONS)} annotation(s) out of {len(unique)} problem(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
