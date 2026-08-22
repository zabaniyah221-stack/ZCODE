# RFC — ZCODE v1.0.21 Data Safety, Reliability, and Update-in-Place

**Date:** 2026-08-21
**Target:** proposed `versionName 1.0.21`, `versionCode 24`
**Production base:** `v1.0.20`, `versionCode 23`, commit `55860ff8059fd1b26e268a53dd3178126e80fbb3`
**Target device:** INFINIX X6532C, Android 14/API 34, `armeabi-v7a` userspace
**Status:** IMPLEMENTED LOCALLY on repair branch; 667 Python/static guards pass and focused source mutations are RED→GREEN. New JVM fault tests are written but still require canonical CI. Android device update, production signing, and release are not yet verified.

## 1. Goal

Ship a focused reliability hotfix which can be installed directly over the
public production APK without uninstalling it, while preserving:

- workspace files, including files not currently open as tabs;
- workspace topology and preferences;
- package environment and `installed.json`;
- application ID and production signing identity;
- Python 3.11 and all currently declared ABIs.

The four-engine intelligence work (Parso, Pyflakes, Jedi, Rope) is explicitly
not part of this hotfix. It remains a separate feature train after device-safe
spikes.

## 2. Confirmed pre-fix failures

1. Settings Clear All called `clearAllDrafts()` without confirmation.
2. Clear All deleted only `openedFiles`, despite promising all `.py` files.
3. Deleting an active file could call `closeFile()`, flush a pending draft, and
   recreate the deleted file.
4. Run ignored the Boolean result from `flushSaveSync()` and logged `SAVE_OK`
   even after save failure.
5. Package activation deleted the entire old package directory before copy and
   restored only `installed.json` on failure.
6. PipScreen owned a blocking engine and coroutine scope which disappeared on
   navigation Back.
7. Async plugin results were not bound to document identity/revision.
8. Rename/type-hint/import transforms could alter strings, defaults, docstrings,
   future imports, or import side effects.
9. Offline wheel lookup mixed underscore and hyphen normalization.
10. The catalog generator could overwrite 342 shipped cards and 237 tested names
    with stale 300/11 source data.
11. Batch execution joined reader threads before killing a timed-out process.
12. RuntimeProbe could return a fallback while its private worker remained alive.
13. Version inequality was mislabeled as an available update even when the
    installed version was newer.
14. Wheel extraction lacked uncompressed-size/entry-count budgets and RECORD
    integrity validation.
15. The repository had no official wrapper JAR; its `gradlew` stub returned zero
    when Gradle was unavailable.

## 3. Workspace transaction

### Clear All

```text
flush every open draft and commit topology
→ inventory every top-level user .py file
→ move files into .zcode-trash/.incoming-<id>
→ fsync metadata manifest
→ atomic directory move to .zcode-trash/last
→ create a fresh main.py
→ atomically commit new workspace preferences
```

The previous restorable deletion is retained until the new deletion reaches its
atomic commit point. An interrupted `.incoming-*` directory is recovered on the
next ViewModel startup. Preferences and `python-env` are outside the deletion
inventory.

### Restore

Restore copies and verifies trash files before changing topology. Existing files
are never overwritten; conflicts receive a `_restored_N.py` suffix. Trash is
removed only after workspace preferences commit. A preference commit failure
removes only the new restore copies and leaves trash restorable.

### Delete and Run

Workspace disk writes and destructive mutations share one monitor. Delete never
calls the flushing `closeFile()` path. Run opens the terminal only after save and
workspace-state commit succeeds; failure logs `RUN_SAVE_FAIL` and remains in the
editor.

## 4. Package generation activation

Each transaction uses unique paths:

```text
site-packages/<canonical>/.incoming-<version>__zcode_<tx>
site-packages/<canonical>/<version>__zcode_<tx>
```

Activation order:

1. validate package/version path segments;
2. normalize extracted file permissions;
3. copy every package to a hidden incoming generation;
4. compare source and copy by relative paths, type, size, and SHA-256;
5. atomically move all incoming directories to unique, non-existing finals;
6. build a new `installed.json` object without mutating the old object;
7. commit it with Android `AtomicFile`;
8. clean old generations only after commit, best effort.

A pre-commit failure deletes only unreferenced incoming/new generations. The old
state and old active directories are not deleted. A crash before state commit
can at worst leave unreferenced storage; a crash after state commit leaves the
new generation active and old storage available for cleanup.

`ZcodeApp` performs AtomicFile recovery before package UI/Python readers can read
`installed.json` directly. SQLite remains a secondary cache and cannot turn a
successful state commit into a false install failure.

Uninstall commits removal from the active pointer before best-effort directory
cleanup and validates that its state path stays under the expected package root.

## 5. Package operation owner

An Activity-scoped `PackageOperationViewModel` owns:

- one `PackageEngineV2`;
- `viewModelScope` and one operation mutex;
- monotonic operation ID;
- requirement snapshot;
- analyze/install/cancel flags;
- console output;
- pending risky plan;
- active package tab;
- package-environment revision.

Analyze, install, and uninstall use this owner. PipScreen can leave composition
without creating a second engine. Reopening it observes the same operation.
The backend also acquires the same global engine lock for uninstall, not merely
the UI guard.

This does not claim that blocking Chaquopy can be force-killed. Cancellation is
still cooperative at documented checkpoints; ownership and visibility are fixed.

## 6. Plugin safety

Every async request captures:

```text
documentId + documentRevision + source snapshot
```

A result is discarded if the active file, revision, or source changed.

- Rename uses Python NAME tokens and preserves strings/comments/formatting. It
  fails closed for invalid identifiers and Python 3.11 f-string expressions it
  cannot safely rewrite.
- Type hints are inserted at AST source positions without reconstructing or
  replacing defaults, positional-only markers, keyword-only markers, varargs,
  or multi-line bodies.
- Organize Imports is temporarily read-only. Python import removal/reordering is
  not semantics-preserving; automatic edits wait for a previewed transactional
  change-set engine.

## 7. Package and runtime hardening

- Offline wheels compare both names with PEP 503 `canonicalize_name`.
- Resolver enforces a 60-unique-package default graph budget.
- Batch timeout kills the process before bounded reader joins.
- RuntimeProbe no longer creates an unowned timeout thread.
- Compatibility reports UPDATE_AVAILABLE only when the tested version compares
  greater than the installed version; unknown forms fail conservative.
- Wheel extraction limits compressed bytes, uncompressed bytes, entry count,
  entry-name length, duplicate names, and traversal.
- Wheel verification requires exactly one dist-info, METADATA identity matching
  the resolved plan, supported Wheel-Version, complete RECORD coverage, sizes,
  and SHA-256/SHA-384/SHA-512 URL-safe hashes.
- Per PEP 427, exact `<dist-info>/RECORD.jws` and `RECORD.p7s` signature files
  may exist without RECORD rows. They are the only unlisted-file exceptions;
  listed signatures, arbitrary unlisted files, phantom rows, weak hashes,
  malformed sizes, duplicate rows, and CSV corruption fail closed.

### 7.1 Explicit package commit boundary

`ActivationCommitBoundary` owns promotion plus the atomic state commit. Before
that commit returns, rollback may remove only new unreferenced generations.
After it returns, cleanup, callbacks, transaction deletion, and journal updates
run independently as best-effort operations; failure is recorded as
`PKG_ACTIVATION_POST_COMMIT_WARN` and cannot delete the active generation.

### 7.2 One workspace gate across check and disk write

Inactive editor callbacks capture document ID, revision, and code. The same
`WorkspaceMutationGate` holds both the stale check and actual `saveFile` call.
Clear/Delete/Rename/Close acquire that gate too. Therefore either the save
commits before the mutation (and the mutation wins last), or the mutation
invalidates it before the check (and the save is a no-op); there is no unlocked
check-to-write window.

### 7.3 GPLv3 Option B

ZABACODE-derived portions retain GPLv3 provenance and contributor attribution.
The combined application is GPL-3.0-only. Independent MIT-authored portions
retain the actual MIT grant in `LICENSES/MIT.txt`; compatibility does not turn
GPL-derived code into MIT. Root `LICENSE`, `NOTICE`, README, About, and exact APK
license assets are guarded together.

References:

- https://peps.python.org/pep-0427/
- https://www.gnu.org/licenses/gpl-3.0.txt
- https://www.gnu.org/licenses/gpl-faq.html#WhatDoesCompatMean
- https://github.com/muzape28-blip/ZABACODE/blob/main/LICENSE

## 8. Generator and toolchain

Catalog generation now fails before writing if source would remove any shipped
package or tested-manifest key. Current 300/11 generator source is intentionally
reported as stale against the 342/237 production assets; those assets are not
regenerated by this hotfix.

The repository now carries the official Gradle 8.5 wrapper JAR, launcher scripts,
and the official binary distribution SHA-256. The wrapper JAR hash is guarded as:

```text
d3b261c2820e9e3d8d639ed084900f11f4a86050a8f83342ade7b6bc9b0d2bdd
```

## 9. Required verification gates

### Local/CI

- focused tests for every changed behavior;
- mutation red-to-green for workspace save gate, plugin rename, and package
  pre-commit deletion invariant;
- JVM tests for WorkspaceTrashManager and Verifier;
- full Python/static suite;
- Kotlin compilation and Android unit tests through the official wrapper;
- debug APK build and artifact inspection;
- workflow source/mirror equality;
- no credentials, cache, or temporary dependencies tracked.

### Production/update

```text
v1.0.20 / code 23 / com.zaba.zcode / production signer
→ install exact signed draft v1.0.21 / code 24
→ no uninstall and no clear-data
```

Pre-update sentinels must include open and closed `.py` files, unique contents,
preferences, package state, and an importable installed package. Post-update must
verify version, signer, all sentinels, Run, import, package operation continuity,
and copyable diagnostics. Publish only the exact draft bytes tested on device;
never rebuild for promotion and never overwrite `v1.0.20`.

## 10. Honest status vocabulary

- **IMPLEMENTED** means source exists on the branch.
- **LOCALLY VERIFIED** requires named local tests.
- **CI VERIFIED** requires the canonical GitHub run.
- **DEVICE VERIFIED** requires the named physical ARMv7 update scenario.
- **RELEASED** requires the verified artifact to be published.

No lower level implies a higher one.
