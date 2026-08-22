# Pending GitHub workflow upload

Arena's GitHub App cannot update `.github/workflows/*` without the
`workflows` permission. The live `build.yml` therefore still skips
`compile-production-source` except on `arena/v1020-production` pull requests.

`build.yml` in this directory is the intended replacement. Upload it through
the GitHub web UI (same path as last session's workflow uploads):

1. Open `.github/workflows/build.yml` on branch `arena/01a0272f-zcode`.
2. Replace the file with `ci/pending/build.yml`.
3. Commit on the same branch.
4. After that push, `compile-production-source` runs on this session branch.

Until then, Kotlin/JVM compiler evidence already exists from the Debug job:

```text
run 32542213874 · ./gradlew testDebugUnitTest assembleDebug · SUCCESS
```
