# Pending GitHub workflow upload

Arena's GitHub App cannot update `.github/workflows/*`: the push of the
unskip commit was rejected by GitHub with

```text
refusing to allow a GitHub App to create or update workflow
`.github/workflows/build.yml` without `workflows` permission
```

(proven again on 2026-08-22, session branch `arena/01a02739-zcode`).
The live `build.yml` therefore still skips `compile-production-source`
except on `arena/v1020-production` pull requests.

`build.yml` in this directory is the intended replacement. Its only diff
against the live file is the `compile-production-source` gate:

```yaml
# old — goes stale every release/session
if: github.event_name == 'pull_request' && github.head_ref == 'arena/v1020-production'

# new — any arena PR: rc/production PRs (Debug build suppressed) plus every
# session-branch PR, without a per-branch name list that rots again
if: github.event_name == 'pull_request' && startsWith(github.head_ref, 'arena/')
```

## How to publish it (user, via GitHub web UI)

1. Open `.github/workflows/build.yml` on branch `arena/01a02739-zcode`.
2. Replace the whole file with `ci/pending/build.yml` (same branch).
3. Repeat for `ci/workflows/build.yml` (mirror must stay byte-identical,
   otherwise the mirror guard test goes red).
4. Commit both edits on `arena/01a02739-zcode`.
5. The next `pull_request` run on the session PR shows
   `compile-production-source` running instead of skipped.

Alternative: grant the Arena GitHub App `workflows: read & write`
permission (repo Settings → GitHub Apps), then ask the agent to push the
same change itself.

## Until then

Kotlin/JVM compiler evidence already exists on every push/PR of this
branch through the Debug build job, which runs the stronger
`./gradlew testDebugUnitTest assembleDebug`:

```text
run 32542213874 (c84d48e, push)     · build SUCCESS
run 32545142292 (c84d48e, push)     · build SUCCESS
run 32545286515 (0a0d471, push)     · build SUCCESS
run 32545291756 (0a0d471, PR)       · build SUCCESS
```
