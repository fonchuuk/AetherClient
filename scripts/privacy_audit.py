#!/usr/bin/env python3
"""Check tracked files only; report locations, never secret values.

This is a narrow regression check, not a guarantee that all personal data is absent.
"""
import re
import subprocess
import sys
from pathlib import Path

SECRET_PATTERNS = (
    re.compile(rb"(?:github_pat_|gh[pousr]_)[A-Za-z0-9_]{15,}"),
    re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
)
PRIVATE_PARTS = {"uploads", "backup", ".config", ".cache"}
PRIVATE_FILES = {"aurora-auth.json", "aurora-launcher.json", "HANDOFF-PROMPT.md"}


def main():
    paths = subprocess.check_output(["git", "ls-files", "-z"]).split(b"\0")
    findings = []
    scanned = 0
    for raw in paths:
        if not raw:
            continue
        path = Path(raw.decode("utf-8"))
        if (PRIVATE_PARTS.intersection(path.parts)
                or path.name in PRIVATE_FILES
                or (path.name.startswith(".env") and path.name != ".env.example")):
            findings.append(f"{path}: private workspace file is tracked")
        if not path.is_file():
            continue
        scanned += 1
        data = path.read_bytes()
        for pattern in SECRET_PATTERNS:
            if pattern.search(data):
                findings.append(f"{path}: possible credential (value withheld)")
    for finding in findings:
        print(finding)
    print(f"Privacy check: {scanned} tracked files scanned; {len(findings)} findings.")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
