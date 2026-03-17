#!/usr/bin/env python3

import argparse
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request


SEMVER_RE = re.compile(r"^v(\d+)\.(\d+)\.(\d+)$")
MAJOR_SELECTOR_RE = re.compile(r"^v(\d+)$")
MINOR_SELECTOR_RE = re.compile(r"^v(\d+)\.(\d+)$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Resolve Seqra release selector to an exact stable tag. "
            "Supported selectors: latest, v<major>, v<major>.<minor>, v<major>.<minor>.<patch>."
        )
    )
    parser.add_argument("--selector", required=True, help="Version selector")
    parser.add_argument(
        "--repository", required=True, help="GitHub repository in owner/name format"
    )
    parser.add_argument("--token", default="", help="Optional GitHub token")
    return parser.parse_args()


def parse_link_header(link_header: str) -> dict[str, str]:
    links: dict[str, str] = {}
    for part in link_header.split(","):
        section = part.strip()
        match = re.match(r"<([^>]+)>;\s*rel=\"([a-zA-Z]+)\"", section)
        if match:
            links[match.group(2)] = match.group(1)
    return links


def github_get_json(url: str, token: str) -> tuple[list[dict], dict[str, str]]:
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "seqra-action-version-resolver",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"

    request = urllib.request.Request(url, headers=headers)

    try:
        with urllib.request.urlopen(request) as response:
            payload = json.loads(response.read().decode("utf-8"))
            link_header = response.headers.get("Link", "")
            links = parse_link_header(link_header) if link_header else {}
            return payload, links
    except urllib.error.HTTPError as exc:
        raise RuntimeError(f"GitHub API request failed with HTTP {exc.code}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Unable to reach GitHub API: {exc.reason}") from exc


def list_stable_releases(
    repository: str, token: str
) -> list[tuple[tuple[int, int, int], str]]:
    query = urllib.parse.urlencode({"per_page": "100"})
    next_url = f"https://api.github.com/repos/{repository}/releases?{query}"
    stable: list[tuple[tuple[int, int, int], str]] = []

    while next_url:
        releases, links = github_get_json(next_url, token)
        for release in releases:
            if release.get("draft") or release.get("prerelease"):
                continue
            tag = release.get("tag_name", "")
            match = SEMVER_RE.match(tag)
            if not match:
                continue
            version = (int(match.group(1)), int(match.group(2)), int(match.group(3)))
            stable.append((version, tag))

        next_url = links.get("next")

    stable.sort(key=lambda item: item[0], reverse=True)
    return stable


def resolve_selector(
    selector: str, stable: list[tuple[tuple[int, int, int], str]]
) -> str:
    if not stable:
        raise ValueError("No stable Seqra releases found")

    if SEMVER_RE.match(selector):
        return selector

    if selector == "latest":
        return stable[0][1]

    major_match = MAJOR_SELECTOR_RE.match(selector)
    if major_match:
        major = int(major_match.group(1))
        for version, tag in stable:
            if version[0] == major:
                return tag
        raise ValueError(f"No stable release found for selector {selector}")

    minor_match = MINOR_SELECTOR_RE.match(selector)
    if minor_match:
        major = int(minor_match.group(1))
        minor = int(minor_match.group(2))
        for version, tag in stable:
            if version[0] == major and version[1] == minor:
                return tag
        raise ValueError(f"No stable release found for selector {selector}")

    raise ValueError(
        "Invalid seqra-version selector. "
        "Use latest, v<major>, v<major>.<minor>, or exact v<major>.<minor>.<patch>."
    )


def main() -> int:
    args = parse_args()

    try:
        stable = list_stable_releases(args.repository, args.token)
        resolved = resolve_selector(args.selector, stable)
    except (RuntimeError, ValueError) as exc:
        print(str(exc), file=sys.stderr)
        return 1

    print(resolved)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
