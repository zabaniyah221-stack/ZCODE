#!/usr/bin/env python3
"""Fail-closed checks for the tiny npm tree used to build ZCODE's editor.

This is not a malware scanner and cannot promise future safety. It enforces the
properties ZCODE actually controls: exact direct pins, lockfile integrity,
known-bad ChainDrop versions/IOCs, and a very small lifecycle-script allowlist.

External incident sources (2026-08):
- https://www.csa.gov.sg/alerts-and-advisories/advisories/ad-2026-009/
- https://safedep.io/keyv-npm-supply-chain-compromise/
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

BAD_VERSIONS = {
    "keyv": {"6.0.0"},
    "flat-cache": {"6.1.24"},
    "file-entry-cache": {"11.1.6", "11.1.7"},
    "cacheable-request": {"13.0.20"},
    "cacheable": {"2.5.1"},
    "cache-manager": {"7.2.10"},
    "@cacheable/utils": {"2.5.1"},
    "@cacheable/memory": {"2.2.1"},
    "@cacheable/node-cache": {"3.1.2"},
    "@cacheable/net": {"2.1.1"},
    "ecto": {"5.0.1"},
}

IOC_FILENAMES = {
    "setup.mjs",
    "Math_Symbol.js",
    "math_init.js",
    "router_runtime.js",
    "gh-token-monitor.sh",
    "gh-token-monitor.service",
}
IOC_TEXT = (
    "npm-cache.com",
    "pypi-get.com",
    "js-mirror.com",
    "Shai-Hulud: Here We Go Again",
    "IfYouBlockThisAPIKey",
    "Bun/1.3.13",
    "0xE1f2395ee43e45A1556EC6438a88c31B83493103",
)
# esbuild's platform-binary verification is the only expected dependency hook.
ALLOWED_INSTALL_SCRIPTS = {("esbuild", "0.28.2")}
ACTIVE_NETWORK_PATTERNS = (
    "window.fetch(",
    "globalThis.fetch(",
    "new XMLHttpRequest",
    "new WebSocket",
    "navigator.sendBeacon",
)


def load_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        raise SystemExit(f"FAIL invalid JSON {path}: {exc}") from exc


def package_name(lock_path: str) -> str:
    return lock_path.removeprefix("node_modules/")


def check(root: Path) -> list[str]:
    errors: list[str] = []
    editor = root / "editor-src"
    manifest_path = editor / "package.json"
    lock_path = editor / "package-lock.json"
    bundle_path = root / "app/src/main/assets/editor/codemirror.bundle.js"
    source_path = editor / "src/editor.js"

    manifest = load_json(manifest_path)
    lock = load_json(lock_path)

    if lock.get("lockfileVersion") != 3:
        errors.append("package-lock.json wajib lockfileVersion 3")

    declared: dict[str, str] = {}
    for group in ("dependencies", "devDependencies"):
        for name, version in manifest.get(group, {}).items():
            declared[name] = str(version)
            if not re.fullmatch(r"\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?", str(version)):
                errors.append(f"dependency tidak dipin exact: {name}={version}")

    packages = lock.get("packages")
    if not isinstance(packages, dict):
        errors.append("package-lock packages map hilang")
        packages = {}

    root_lock = packages.get("", {})
    for group in ("dependencies", "devDependencies"):
        if root_lock.get(group, {}) != manifest.get(group, {}):
            errors.append(f"root lock {group} tidak sinkron package.json")

    for path, meta in packages.items():
        if not path.startswith("node_modules/"):
            continue
        name = package_name(path)
        version = str(meta.get("version", ""))
        integrity = str(meta.get("integrity", ""))
        if not integrity.startswith(("sha512-", "sha384-", "sha256-")):
            errors.append(f"integrity hilang/lemah: {name}@{version}")
        if version in BAD_VERSIONS.get(name, set()):
            errors.append(f"KNOWN MALICIOUS ChainDrop: {name}@{version}")
        if meta.get("hasInstallScript") and (name, version) not in ALLOWED_INSTALL_SCRIPTS:
            errors.append(f"install script tidak diizinkan: {name}@{version}")

    # Direct pins must resolve to exactly the same locked version.
    for name, expected in declared.items():
        actual = packages.get(f"node_modules/{name}", {}).get("version")
        if actual != expected:
            errors.append(f"pin/lock drift: {name} expected={expected} actual={actual}")

    for path in root.rglob("*"):
        if not path.is_file() or ".git" in path.parts or "node_modules" in path.parts:
            continue
        rel = path.relative_to(root).as_posix()
        if path.name in IOC_FILENAMES:
            errors.append(f"IOC filename ditemukan: {rel}")
        if rel in {".claude/settings.json", ".vscode/tasks.json"}:
            errors.append(f"auto-run agent/IDE hook dilarang: {rel}")
        if path.stat().st_size <= 2_000_000:
            try:
                text = path.read_text(encoding="utf-8")
            except (UnicodeDecodeError, OSError):
                continue
            for marker in IOC_TEXT:
                if marker in text and path != Path(__file__).resolve():
                    errors.append(f"IOC string {marker!r} ditemukan: {rel}")

    source = source_path.read_text(encoding="utf-8")
    bundle = bundle_path.read_text(encoding="utf-8")
    for pattern in ACTIVE_NETWORK_PATTERNS:
        if pattern in source or pattern in bundle:
            errors.append(f"primitive network editor dilarang: {pattern}")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    root = args.root.resolve()
    errors = check(root)
    if errors:
        print("FAIL npm/editor supply-chain guard")
        for error in sorted(set(errors)):
            print(f"- {error}")
        return 1
    print("OK npm/editor supply-chain guard: exact pins, integrity, lifecycle, IOC, network")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
