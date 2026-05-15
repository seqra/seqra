#!/usr/bin/env python3
"""Patch sha256 values in a GoReleaser-generated Homebrew cask file.

For each on_intel/on_arm block, extract the archive name from the `url`
line, compute SHA-256 of the matching local archive, and rewrite the
`sha256` line in that block. Order-independent (sha256 may appear before
or after the url). Exits non-zero on any anomaly so a silent format
change cannot ship stale hashes.
"""
from __future__ import annotations

import argparse
import hashlib
import pathlib
import re
import sys

BLOCK_RE = re.compile(
    r"(on_(?:intel|arm)\s+do\n)(.*?)(\n[ \t]*end\b)",
    re.DOTALL,
)
URL_RE = re.compile(r'url\s+"[^"]*/([^"/]+\.(?:tar\.gz|zip))"')
SHA_RE = re.compile(r'(sha256\s+")[a-f0-9]{64}(")')


def main(cask: pathlib.Path, dist: pathlib.Path) -> int:
    text = cask.read_text()
    errors: list[str] = []
    patched = 0

    def patch_block(match: re.Match[str]) -> str:
        nonlocal patched
        header, body, footer = match.group(1), match.group(2), match.group(3)
        url_match = URL_RE.search(body)
        if not url_match:
            errors.append(f"block missing url line: {match.group(0)!r}")
            return match.group(0)
        name = url_match.group(1)
        archive = dist / name
        if not archive.is_file():
            errors.append(f"archive not found: {archive}")
            return match.group(0)
        sha = hashlib.sha256(archive.read_bytes()).hexdigest()
        new_body, n = SHA_RE.subn(rf"\g<1>{sha}\g<2>", body, count=1)
        if n != 1:
            errors.append(f"no sha256 line in block for {name}")
            return match.group(0)
        patched += 1
        return header + new_body + footer

    new_text = BLOCK_RE.sub(patch_block, text)

    if errors:
        for err in errors:
            print(f"ERROR: {err}", file=sys.stderr)
        return 1
    if patched == 0:
        print("ERROR: no on_intel/on_arm blocks found in cask", file=sys.stderr)
        return 1

    cask.write_text(new_text)
    print(f"Patched sha256 in {patched} cask block(s): {cask}")
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--cask",
        type=pathlib.Path,
        required=True,
        help="path to the cask file to patch in-place",
    )
    parser.add_argument(
        "--dist",
        type=pathlib.Path,
        required=True,
        help="directory containing the release archives referenced by the cask",
    )
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    sys.exit(main(args.cask, args.dist))
