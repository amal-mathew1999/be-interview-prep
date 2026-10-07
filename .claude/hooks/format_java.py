"""PostToolUse(Edit|Write): run Spotless on the edited .java file in whichever checkout owns it.

Exit 2 feeds formatter errors (usually syntax errors) back to Claude.
"""

import json
import os
import re
import subprocess
import sys


def find_root(path: str):
    d = os.path.dirname(os.path.abspath(path))
    while True:
        if os.path.isfile(os.path.join(d, "pom.xml")) and os.path.isfile(os.path.join(d, "mvnw")):
            return d
        parent = os.path.dirname(d)
        if parent == d:
            return None
        d = parent


def main() -> int:
    try:
        data = json.load(sys.stdin)
    except json.JSONDecodeError:
        return 0
    path = (data.get("tool_input") or {}).get("file_path", "")
    if not path.endswith(".java") or not os.path.isfile(path):
        return 0
    root = find_root(path)
    if not root:
        return 0

    rel_parts = os.path.relpath(os.path.abspath(path), root).replace("\\", "/").split("/")
    files_regex = ".*[\\\\/]" + "[\\\\/]".join(re.escape(p) for p in rel_parts)
    mvnw = os.path.join(root, "mvnw.cmd" if os.name == "nt" else "mvnw")
    proc = subprocess.run(
        [mvnw, "-q", "-B", "spotless:apply", f"-DspotlessFiles={files_regex}"],
        cwd=root,
        capture_output=True,
        text=True,
        timeout=170,
        shell=(os.name == "nt"),
    )
    if proc.returncode != 0:
        tail = "\n".join((proc.stdout + proc.stderr).strip().splitlines()[-25:])
        print(f"[harness format] Spotless failed for {path} (syntax error?):\n{tail}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
