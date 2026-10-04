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
- Cards app-wide (`VmCard`) are flat tonal surfaces with rounded corners and no outline, so
  Servers, Projects, Settings and Connectors match the chat. Server rows lost the
  "Never connected"/auth/environment badges; only "Production" is called out.
- Theme: warm neutrals and a clay accent in place of cold grey and blue.

## OmniRoute integration audit

**Fixed in this PR**

| Problem | Effect | Fix |
|---|---|---|
| Model list only refreshed by the Settings connection test | Picker showed whatever was synced weeks ago | `OmniRouteModelCatalog`: persisted list + synced-at time, auto-refresh when the chat opens on a list older than 15 min, "Sync models now" in the picker |
| Chat always sent its own model id (`claude-3-7-sonnet-latest`, later `sonnet`) | The model picked in Settings was ignored and the gateway got an id it may not serve | One picker; Claude aliases are never sent to the gateway |
| Provider chosen globally in Settings | Switching between Claude Code and a gateway model meant a trip to Settings | The chosen model decides the backend; switching drops the session and re-checks health |
| Agent runs started from scratch every message | Follow-ups ("now add a test for it") reached the model with no context | In-memory conversation history per session (LRU of 16); session id reported back |
| `https://host/v1` as base URL | Requests went to `/v1/v1/models` and failed | Trailing `/v1` and slashes are normalised |
| Default gateway `https://ai.vmstudio.digital` | Every install pointed at a private gateway | No default; blank URL gets a clear "add your gateway" message |

**Added after the audit**

- `web_fetch` tool (public pages only; private/LAN addresses refused).
- Streaming in the agent loop, with a non-streaming fallback per turn.
- In-app preview of `.html` files the agent writes (sandboxed WebView).
- Context survives app restarts (rebuilt from the saved transcript).
- File and image attachments: uploaded to `.xcodes/attachments/`, paths added to the prompt; images ≤ 4 MB also sent inline to the gateway.

**Still open, in priority order**

1. **The "allow tools" switch does nothing in chat.** `OmniRouteProvider.run` enables the tool loop whenever a server is set (`aiToolsEnabled || serverId.isNotBlank()`), and the chat always has a server. Decide: either honour the switch or remove it from Settings.
3. **Every health check sends two billed chat requests** (dialect probes with model `probe`). Cache the detected dialect per base URL instead of re-probing.
5. **No context trimming.** Long tool-heavy conversations grow until the gateway rejects them. Trim oldest turns while keeping tool-call/result pairs together.
6. **Cost is always $0.** The gateway bills upstream; show tokens only, or read cost if the gateway reports it.

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
4. Chat screen is split into `AgentChatScreen` (host), `ChatTranscript`, `ChatComposer`, `ChatPanels`.
5. Settings is one long scroll of option cards; group it into a few pages.
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
