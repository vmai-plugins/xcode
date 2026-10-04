# Audit — universal Claude Code client

Scope: turn the app from a "VM Studio" ecosystem tool into a generic mobile client
for Claude Code running on any SSH-reachable machine, with a Claude-style UI.

## Done in this change

| Area | Before | After |
|---|---|---|
| Project Hub | WordPress REST sync on every launch, default API key, MCP tool list, "audit project" action | Removed (`ProjectsHubClient`, `SyncManager`, `AgentManager`, models, all UI hooks) |
| Seeded servers | Two root VPS profiles with real IPs created on first launch | Removed. Servers come only from what the user adds |
| Navigation | 5-tab bottom bar / rail (Home, Projects, Servers, AI Agent, Settings) | No footer. Slide-out drawer: New chat, Recents, Servers, Projects, Settings |
| Start screen | Home dashboard (stat cards, activity feed, tagline) | Opens straight into a chat on the last-used server; "Connect a server" if none |
| Home dashboard | `:feature:dashboard` module | Deleted (update banner already lives in Settings) |
| Connectors | Hub sync card + MCP tool list + Drive + runner | Drive backup + SSH runner only |

## Chat restyle (second pass)

- One quiet top bar: menu, project title, files, new chat. Status text only while connecting.
- Removed the workspace card (it showed a hardcoded "main" branch), the permission chip row
  and the gradient send button.
- Replies are plain text on the page; your messages sit in a soft rounded block.
- Composer: folder, permission mode and model pickers live inside it; round send button.
- Model picker now offers `sonnet` / `opus` / `haiku` (aliases the CLI accepts) instead of
  stale 3.x ids and an OmniRoute option that sent an invalid `--model`.
- Tool calls collapse to one muted line ("Ran 5 tools ›"); "Thinking" is a text toggle;
  tool output and diffs use theme colours so they read in light mode too.
- Theme: warm neutrals and a clay accent in place of cold grey and blue.

## Findings still open

**Security (act on these first)**
1. The Hub API key (`xc_a081…`) was committed and is still in git history. Rotate it
   server-side; deleting the file does not revoke it.
2. Devices that already ran an older build keep the two seeded VPS rows. Delete them in
   Servers (or ship a one-off migration).
3. `scripts/build-app.sh` and `scripts/publish-app.sh` hard-code `root@31.97.63.239` and
   the vmstudio.digital paths. They are maintainer tooling, not app code, but they belong
   in a private repo or behind env vars with no default.

**UI**
4. `AgentChatScreen.kt` is still ~1,500 lines; split transcript rows and composer out.
5. Servers/Projects/Settings still use the older cards (they pick up the new colours but
   not the flat layout).
7. `ConnectorsScreen` has no entry point (it had none before either). Either link it
   from Settings or delete it.
8. Tablet: the rail is gone; a permanent drawer at expanded width would use the space.

**Structure**
9. `feature/activity`, `feature/git`, `feature/onboarding` contain only build files.
   Remove them or implement them.
10. README / ARCHITECTURE still describe the VM Studio ecosystem and a 29-module count.

## Not verified
The sandbox has no Android SDK, so nothing was compiled or run. Brace/paren balance and
dangling references were checked by script and grep. Run `./build.sh :app:assembleDebug`
(or let CI do it) before merging.
