# Bug Checklist

## Fixed in this audit

- [x] Reject ZIP path traversal and unsafe archive paths before extraction.
- [x] Surface bad GitHub URLs, HTTP errors, invalid/empty archives, extraction failures, and transfer failures instead of reporting a successful import.
- [x] Cap archive size, extracted size, and entry count to bound storage and decompression use.
- [x] Stage GitHub archives before writing them to the workspace; clean partial imports after failure or cancellation.
- [x] Fail local directory creation when storage rejects it; use the app's writable projects directory instead of a hard-coded shared-storage path.
- [x] Verify linked folders exist and are directories; add a persistent phone-folder picker and prevent a selected phone folder from being switched to a VPS target.
- [x] Remove the misleading Google Drive workspace-creation option; Drive backup remains a separate feature.
- [x] Require a completed terminal event before reporting a transfer as successful; remove failed or cancelled partial downloads from cache.
- [x] Do not run setup commands for empty or linked workspaces; save a successfully cloned workspace even if its optional setup command fails, and offer an explicit way to open it.
- [x] Prevent duplicate workspace creation while a create operation is in progress and validate GitHub URLs before attempting a clone.

## Known limitations / follow-up

- [x] Use the stored GitHub token for private VPS clones through short-lived, permission-restricted askpass files, then remove those files.
- [x] Authenticate private phone archive downloads through the GitHub API, and only follow HTTPS redirects to `codeload.github.com`.
- [ ] Phone GitHub import remains a source snapshot, not a Git clone, so it does not include `.git` history.
- [x] Re-run the Projects and Files unit test tasks and build a fresh debug APK.

## Earlier checklist items not reproduced

- [ ] Re-check the previously suspected editor null assertions, terminal coroutine null assertion, GitHub settings cancellation handling, and one-click VPS navigation behavior. Targeted searches during this pass did not reproduce those reported patterns.
