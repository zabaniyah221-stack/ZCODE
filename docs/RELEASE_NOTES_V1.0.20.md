# ZCODE v1.0.20

v1.0.20 was published on 2026-08-21 from production workflow commit
`55860ff8059fd1b26e268a53dd3178126e80fbb3`. The workflow and public release are
evidenced; this repository does not contain a final physical-device UAT report
or an independent post-publication re-download signer/hash verification. The
one-build contract attached the signed draft bytes without a promotion rebuild.

```text
Package      : com.zaba.zcode
Label        : ZCODE
Version      : 1.0.20
Version code : 23
Android      : 8.0+ / API26+
Python       : 3.11
Chaquopy     : 17.0.0
R8           : enabled, compatibility mode
Obfuscation  : disabled
Resource trim: disabled
```

```text
Production CI run : 32472551816 — SUCCESS
Public release    : v1.0.20 — RELEASED
Published at      : 2026-08-21T11:07:48Z
APK asset         : ZCODE-v1.0.20.apk (34,682,027 bytes)
Device UAT log    : NOT EVIDENCED IN REPO
Independent hash : NOT VERIFIED BY REPO AUDIT
```

## Highlights

### Optimized daily-use performance

The release-like optimized build removes the dominant Debug-build jank observed
on the target ARMv7 device. Tap, swipe, scroll, tab changes, sidebar navigation,
and Settings transitions were device-verified as smooth before production
promotion.

### CodeMirror 6 editor

- offline bundled editor;
- document-scoped EditorState and Undo/Redo history;
- stale-tab-safe callbacks carrying document identity;
- syntax highlighting, autocomplete, Find, lint diagnostics, whitespace guard,
  traceback navigation, and editor tools;
- accessible name `Editor kode Python`;
- WCAG-AA line-number contrast;
- pinch zoom without false IME opening;
- per-tab zoom isolation observed during device UAT.

TalkBack spoken output for the editor label has not been physically verified.

### Terminal and execution

- Python 3.11 remains pinned for ARMv7 native-wheel compatibility;
- terminal keyboard can reopen after IME dismissal;
- IME Done, input, scroll/fling, selection, copy/share, and `^C` were
  device-verified;
- semantic package logs distinguish INFO/WARN/WAIT/OK/FAIL/STOP/RAW;
- cancel is STOP, not a false failure.

### Package reliability and native runtime safety

- source/network/not-found/package-availability failures remain distinct;
- partial HTTP reads use bounded retry;
- PEP 440 specifiers apply to local, PyPI, and Chaquopy candidates before
  ranking;
- uninstall remains conservative and warns about reverse-dependency limits;
- native extension mutations trigger a verified fresh-process rebirth after
  workspace flush;
- Python packages and samples retain the v1.0.19 evidence baseline.

## One-build release model

The production workflow builds exactly one signed APK:

```text
ZCODE-v1.0.20.apk
```

CI verifies package, version, non-debuggable/non-profileable status, bundled
assets, R8 output, and signer certificate. The expected public signer SHA-256
is:

```text
401392193b734263c8ecce93e12be1f7f307203afe4282dc2550094088f38bd2
```

The same APK bytes are uploaded as an internal Actions artifact and attached to
a private draft release. Device UAT must use those exact bytes. Publishing the
draft performs no rebuild.

## Installation and data warning

ZCODE RC uses `com.zaba.zcode.rc`; production uses `com.zaba.zcode`. Android
therefore treats them as separate applications and does not migrate private app
data automatically.

Before removing RC:

1. copy/export every important project;
2. install production ZCODE alongside RC;
3. verify all copied files in production;
4. only then uninstall RC if desired.

Future production updates signed by the same key and using a higher versionCode
should install in place without uninstalling. Update continuity will receive its
first device evidence on the next real production update because this release
intentionally uses a one-build model.

## Not included

- Project Workbench/Explorer/Git/plugin redesign (parked for v1.0.25);
- Python 3.12+;
- CodeMirror replacement;
- R8 obfuscation or resource shrinking;
- automatic Android cloud backup;
- a claim that production update continuity is already device-verified.
