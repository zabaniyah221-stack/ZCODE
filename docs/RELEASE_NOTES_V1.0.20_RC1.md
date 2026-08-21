# ZCODE v1.0.20-rc1 — Internal Release Candidate

**Status:** internal candidate, not a public release
**Package:** `com.zaba.zcode.rc`
**Label:** `ZCODE RC`
**Version:** `1.0.20-rc1` (`versionCode 23`)
**Target:** Android 8.0+/API26, including ARMv7
**Signing:** ephemeral CI debug key; not the production key

## Purpose

RC1 promotes the optimized/R8 build which removed the dominant Debug-build
jank on the target INFINIX X6532C. It exists as a separate application so it
cannot overwrite Debug or reserve the production identity
`com.zaba.zcode` with an ephemeral certificate.

RC1 is feature-frozen. Its purpose is final regression, update/data-safety
planning, and release readiness—not Workbench expansion.

## Included foundations

- CodeMirror 6 offline editor with document-scoped state and Undo/Redo;
- Python 3.11 + Chaquopy 17.0.0, including ARMv7;
- optimized non-debuggable R8 compatibility-mode build;
- conservative WebView/Chaquopy/rebirth keep rules;
- native-runtime rebirth after native package mutations;
- semantic package logs and conservative uninstall;
- package resolver source/error/specifier fixes;
- Library/Samples and Bokeh 3.3.4 device evidence;
- terminal keyboard reopen after IME dismissal;
- editor accessibility name, WCAG-AA gutter contrast, and pinch zoom;
- multi-touch guard preventing pinch completion from opening the IME.

## Device-verified behavior inherited by RC1

On INFINIX X6532C, Android 14/API34, `armeabi-v7a`:

- optimized tap, swipe, scroll, tab/menu/sidebar/Settings transitions;
- terminal keyboard close/reopen, IME Done, selection, Salin, Bagikan, and `^C`;
- editor pinch zoom in/out without false IME opening;
- single tap still opens IME;
- typing, Backspace, Enter, selection handles, copy/paste;
- horizontal/vertical editor scrolling, edge-swipe sidebar, rotation;
- per-tab zoom isolation observed within the tested session;
- Python run and Diagnostics sanity.

TalkBack spoken output for `aria-label="Editor kode Python"` has not been
physically verified. Browser accessibility tree and Lighthouse passed.

## Build contract

```text
Build type       : rc
Debuggable       : false
Profileable      : true
R8               : ON, compatibility mode
Obfuscation      : OFF
Resource shrink  : OFF
Package          : com.zaba.zcode.rc
Task affinity    : com.zaba.zcode.rc
Artifact         : ZCODE-v1.0.20-rc1
User-facing APKs : exactly one
```

The canonical Debug workflow excludes RC branch pushes. On a future RC pull
request it may run tests, but its Debug APK build/upload job is skipped. The RC
workflow never references production signing secrets.

## Installation warning

The CI debug signing key may differ between runs. Android may reject an update
of an older `ZCODE RC`; uninstalling the old RC can erase its private app data.
Do not keep the only copy of an important project inside RC until backup/export
and stable signing are complete.

`ZCODE Performance`, `ZCODE Debug`, `ZCODE RC`, and future production ZCODE use
separate application IDs and data directories.

## Explicit non-goals

- public release;
- production signing integration;
- Play Store/F-Droid onboarding;
- obfuscation or resource shrinking;
- Python/Chaquopy/Compose/CodeMirror upgrades;
- Project Workbench, Explorer, Git/GitHub, or plugin marketplace;
- backup/restore implementation.

Project Workbench remains parked for v1.0.25.
