# VMStudio Code — Master Android Development Specification

## ROLE

You are a principal Android architect, senior Kotlin engineer, UX engineer, backend/infrastructure engineer, security engineer, and AI-agent engineer.

Your task is to build **VMStudio Code**, a production-grade Android application designed to be a serious mobile alternative to Claude Code for professional developers.

Do NOT build a demo, prototype, mock UI, proof-of-concept, or collection of disconnected screens.

Build a real, modular, extensible Android development environment capable of connecting to remote servers, managing projects, editing code, running commands, interacting with Git, transferring files, and using an AI coding agent.

The application must be designed so that future features can be added without rewriting the architecture.

---

# 1. PRODUCT

Application name:

**VMStudio Code**

Primary purpose:

> A professional AI-powered mobile development environment that allows users to connect to remote development machines, open and manage projects, inspect/edit code, execute commands, manage files, use Git, and delegate coding tasks to an AI coding agent.

The experience should combine concepts from:

- Claude Code
- VS Code
- GitHub Codespaces
- Termux
- Remote SSH
- SFTP clients
- modern AI coding agents

But VMStudio Code must have its own architecture, UX, branding, and implementation.

Do not clone proprietary UI.

---

# 2. CORE ARCHITECTURE

Use a clean, scalable architecture.

Preferred stack:

- Kotlin
- Jetpack Compose
- Material 3
- Android Jetpack
- MVVM / Clean Architecture
- Kotlin Coroutines
- Flow / StateFlow
- Hilt dependency injection
- Room database
- DataStore
- WorkManager
- Android Keystore
- SSH/SFTP library suitable for Android
- Git implementation suitable for Android
- WebSocket/SSE where appropriate
- REST APIs where appropriate

Separate the application into modules/services such as:

```text
app
core
core-ui
core-network
core-security
core-database
core-terminal
core-editor
core-git
core-ssh
core-sftp
core-ai
core-project
core-connectors
feature-dashboard
feature-projects
feature-editor
feature-terminal
feature-ai
feature-git
feature-files
feature-servers
feature-settings
```

Avoid tightly coupled feature code.

---

# 3. APPLICATION STRUCTURE

Create a professional navigation system.

Primary sections:

1. Home
2. Projects
3. Servers
4. Workspace
5. AI Agent
6. Terminal
7. Files
8. Git
9. Connectors
10. Tasks
11. Activity
12. Settings

Use adaptive layouts.

On phones:

- bottom navigation / navigation rail
- compact workspace UI
- bottom sheets
- expandable panels

On tablets/foldables:

- multi-pane layout
- project tree + editor + terminal
- persistent navigation
- resizable panels where practical

---

# 4. ONBOARDING

Create a professional first-run experience.

Steps:

### Welcome

Explain:

> Code anywhere. Connect anywhere. Build with AI.

### Setup

Allow the user to configure:

- VMStudio account
- OmniRoute AI
- SSH servers
- Git identity
- connectors
- workspace preferences

Do not force unnecessary configuration.

The user should be able to enter the application and create/connect to a project quickly.

---

# 5. PROJECT MANAGEMENT

Projects are a first-class concept.

Each project must have:

- project name
- description
- local/remote status
- server
- remote path
- project type
- Git repository
- branch
- technology stack
- environment
- last opened time
- AI configuration
- project instructions
- environment variables metadata
- tasks
- activity
- recent files

Support project types:

- Android
- Kotlin
- Java
- Flutter
- React
- React Native
- Next.js
- Node.js
- PHP
- Laravel
- WordPress
- WooCommerce
- Python
- Django
- FastAPI
- HTML/CSS/JS
- Docker
- generic Linux projects

Do not hardcode project types into business logic.

Use a project configuration system.

---

# 6. PROJECT CONFIGURATION

Every project should have a configuration screen.

Example:

```text
Project
├── General
├── Remote Server
├── Workspace
├── Runtime
├── Git
├── Build
├── Environment
├── AI
├── Commands
├── Tasks
├── Permissions
└── Advanced
```

Allow configurable:

- project root
- server
- SSH user
- remote directory
- build commands
- test commands
- lint commands
- deployment commands
- startup commands
- package manager
- runtime
- framework
- Git branch
- AI instructions
- excluded directories
- sensitive files
- environment variable names
- allowed AI actions

---

# 7. SSH

SSH must be a major feature.

Support:

- SSH host
- hostname/IP
- port
- username
- password authentication
- SSH private key
- passphrase protected keys
- known hosts
- host fingerprints
- connection timeout
- keepalive
- reconnect
- connection status
- multiple servers
- server groups
- server aliases

Security:

- Never store passwords in plaintext.
- Store credentials using Android Keystore-backed secure storage.
- Encrypt sensitive local data.
- Never log credentials.
- Never expose private keys in logs.
- Verify host identity.
- Warn about changed host fingerprints.

Support:

```text
ssh user@server
```

conceptually through the application without requiring the user to manually open another app.

---

# 8. SFTP / REMOTE FILE SYSTEM

Build a full remote file manager.

Support:

- browse directories
- create folder
- create file
- rename
- delete
- move
- copy
- upload
- download
- multi-select
- search
- sort
- file information
- permissions
- refresh
- transfer queue
- progress
- retry
- cancellation

Support large files efficiently.

Do not load entire files into memory unnecessarily.

---

# 9. REMOTE TERMINAL

Create a professional terminal experience.

Features:

- interactive shell
- command history
- multiple terminal sessions
- tabs
- reconnect
- resize handling
- ANSI colors
- copy/paste
- select text
- clear terminal
- command search
- environment information
- running process visibility
- background command handling

Support common shells:

- bash
- sh
- zsh

Do not assume bash exists.

Detect available shell.

---

# 10. REMOTE CONTROL

VMStudio Code must allow users to control remote development environments.

Examples:

```text
Start server
Stop server
Restart service
Run build
Run tests
Run migrations
Install dependencies
Run deployment
Check logs
Check system status
```

Commands must be project-aware.

Create a safe command execution layer.

Never silently execute destructive commands.

For dangerous operations require explicit confirmation.

Examples:

```text
rm -rf
DROP DATABASE
git reset --hard
docker system prune
production deployment
service restart
```

The AI must also respect these rules.

---

# 11. CODE EDITOR

Build a serious mobile code editor.

Required:

- syntax highlighting
- line numbers
- search
- replace
- go to line
- undo/redo
- multiple files
- tabs
- code folding
- bracket matching
- indentation
- auto-indent
- file tree
- recent files
- modified-file indicators
- unsaved changes
- save state
- encoding handling

Architecture must allow future LSP integration.

Do not create an editor that only supports plain text.

Design an abstraction for language services.

---

# 12. PROJECT EXPLORER

Create a VS Code-like project explorer.

Example:

```text
PROJECT
│
├── app/
├── src/
├── public/
├── resources/
├── tests/
├── .git/
├── package.json
└── README.md
```

Support:

- expand/collapse
- context menu
- new file
- new folder
- rename
- delete
- copy path
- open terminal here
- AI actions
- Git status

---

# 13. AI ENGINE

This is one of the most important components.

VMStudio Code must use:

**OmniRoute**

as the central AI gateway.

Primary endpoint:

**https://ai.vmstudio.digital**

Do NOT hardcode secrets into the Android application.

Create an AI provider abstraction:

```text
AIProvider
├── OmniRouteProvider
├── OpenAIProvider
├── AnthropicProvider
├── GeminiProvider
├── OpenRouterProvider
└── CustomProvider
```

OmniRoute should be the primary production integration.

---

# 14. OMNIROUTE INTEGRATION

Create a robust API client.

Support:

- authentication
- model discovery
- streaming responses
- conversation history
- cancellation
- retry
- timeout
- error handling
- token usage
- model selection
- context limits
- connection health
- API status

Configuration:

```text
AI Provider: OmniRoute
Base URL: https://ai.vmstudio.digital
API Key: secure credential
Model: selectable
```

Never embed an API key in source code.

Never commit credentials.

---

# 15. AI CODING AGENT

VMStudio Code should not be just an AI chatbot.

Build an **AI Coding Agent**.

The agent should be capable of:

1. Understanding the project
2. Inspecting files
3. Searching the repository
4. Reading relevant code
5. Creating files
6. Editing files
7. Deleting files with confirmation
8. Running terminal commands
9. Running tests
10. Inspecting errors
11. Fixing errors
12. Reviewing changes
13. Using Git
14. Explaining changes
15. Planning multi-step tasks

The workflow should resemble:

```text
User Request
     ↓
AI Planner
     ↓
Project Understanding
     ↓
Tool Selection
     ↓
File / Terminal / Git Operations
     ↓
Result
     ↓
Validation
     ↓
Fix if necessary
     ↓
Final Summary
```

---

# 16. AI TOOL SYSTEM

Implement tools rather than allowing the AI to directly manipulate the application.

Example tool registry:

```text
read_file
write_file
edit_file
delete_file
list_directory
search_code
search_files
run_command
get_terminal_output
git_status
git_diff
git_log
git_branch
git_checkout
git_commit
git_pull
git_push
create_project
upload_file
download_file
inspect_environment
run_tests
run_build
```

Every tool must define:

- name
- description
- parameters
- permissions
- confirmation requirement
- execution handler
- result format
- error format

---

# 17. AI PERMISSION SYSTEM

Create AI autonomy levels.

### Ask Every Time

AI proposes every operation.

### Safe Auto

AI can automatically:

- read files
- search files
- inspect Git
- inspect project
- run safe commands

### Developer Mode

AI can modify files and execute approved commands.

### Full Agent Mode

AI can autonomously:

- edit files
- create files
- run tests
- run builds
- iterate on errors

Still require confirmation for destructive or production-sensitive operations.

The user must always be able to stop the agent.

---

# 18. AI CONTEXT ENGINE

Do not send the entire project to the AI.

Implement intelligent context selection.

The context engine should consider:

- current file
- selected code
- open files
- project structure
- Git diff
- relevant files
- imports
- dependencies
- error logs
- terminal output
- project instructions
- previous conversation
- task history

Implement context limits.

Prioritize relevant information.

---

# 19. PROJECT AI INSTRUCTIONS

Every project should support an instruction file/configuration.

Example:

```text
VMSTUDIO.md
```

The AI should read it before performing project tasks.

It can contain:

- architecture rules
- coding standards
- build commands
- test commands
- deployment rules
- forbidden operations
- project-specific instructions

Support hierarchical instructions later:

```text
global
workspace
project
directory
```

---

# 20. AI CHAT UI

Create a professional AI coding interface.

Each conversation should show:

- user request
- AI response
- tool execution
- files changed
- commands executed
- errors
- approvals
- progress
- final result

Do not hide agent activity.

Example:

```text
Analyzing project...

✓ Found React application
✓ Inspected package.json
✓ Located authentication module

AI PLAN
1. Modify login component
2. Update API client
3. Add validation
4. Run tests

[Approve Plan]

Executing...

✓ Edited Login.tsx
✓ Edited api/auth.ts
✓ Tests passed

Changes:
3 files modified
```

---

# 21. DIFF REVIEW

Every AI modification must be reviewable.

Show:

- before
- after
- unified diff
- file name
- additions
- deletions

Actions:

- Accept
- Reject
- Revert
- Open File

Never make large AI changes invisible.

---

# 22. GIT

Implement Git integration.

Support:

- status
- diff
- branches
- checkout
- create branch
- commit
- push
- pull
- fetch
- merge
- log
- tags
- stash

Show file status:

```text
M modified
A added
D deleted
? untracked
```

Create a visual Git dashboard.

AI can use Git tools subject to permissions.

---

# 23. CONNECTORS

Create a connector framework.

Initial connector:

### Google Drive

Support:

- authentication
- browse files
- search
- upload
- download
- create folder
- rename
- move
- project import/export

Architecture should allow future connectors:

```text
Google Drive
GitHub
GitLab
Bitbucket
Dropbox
OneDrive
Google Cloud
AWS
Vercel
Netlify
Docker
```

Do not hardcode connector-specific logic throughout the app.

Use:

```text
Connector
ConnectorAuth
ConnectorFileProvider
ConnectorStorage
```

---

# 24. GOOGLE AUTHENTICATION

Use secure OAuth.

Never ask users to paste Google passwords.

Use Android-compatible OAuth flow.

Store tokens securely.

Handle:

- login
- logout
- token refresh
- expired tokens
- revoked permissions

Request only required scopes.

---

# 25. LOCAL WORKSPACE

Support local project work where Android permissions allow it.

Support:

- Android Storage Access Framework
- local folders
- import/export
- offline project browsing
- cached files
- local editing

Do not assume unrestricted filesystem access.

Respect Android sandbox/security rules.

---

# 26. REMOTE + LOCAL HYBRID WORKSPACE

The user should be able to work in:

```text
LOCAL
REMOTE SSH
SFTP
CLOUD CONNECTOR
```

Projects must clearly indicate location.

Example:

```text
VMStudio Website
● SSH: Production VPS
Path: /var/www/vmstudio
Branch: main
```

---

# 27. TRANSFER MANAGER

Create a centralized transfer manager.

Show:

```text
Uploads
Downloads
Queued
Active
Completed
Failed
```

Support:

- progress
- speed
- remaining time
- pause where technically possible
- cancel
- retry
- error details

Transfers must continue safely using Android background mechanisms where possible.

---

# 28. TASK MANAGEMENT

AI coding work should become tasks.

Task:

```text
Title
Description
Project
Priority
Status
AI conversation
Files changed
Commands
Tests
Created
Updated
```

Statuses:

```text
Todo
Planning
Running
Waiting Approval
Testing
Completed
Failed
Cancelled
```

---

# 29. ACTIVITY CENTER

Create an activity timeline.

Example:

```text
21:10 Connected to VPS
21:11 Opened project
21:12 AI started task
21:13 4 files modified
21:14 Tests started
21:15 Tests passed
21:15 Git diff created
```

Allow filtering by:

- project
- server
- AI
- Git
- transfers
- errors

---

# 30. SERVER DASHBOARD

For every SSH server show:

- connection status
- hostname
- OS
- CPU
- RAM
- disk
- uptime
- active processes where available
- project count
- recent activity

Do not require root access.

Gracefully handle unavailable metrics.

---

# 31. ERROR HANDLING

Every subsystem must have structured error handling.

Never show:

```text
Unknown error
```

when useful information is available.

Errors should include:

- what happened
- likely reason
- suggested action
- retry option

Examples:

```text
SSH Connection Failed

Host: server.example.com
Reason: Connection timed out

[Retry]
[Check Connection]
[Edit Server]
```

---

# 32. OFFLINE MODE

The application should remain useful without internet.

Offline capabilities:

- view cached projects
- edit cached files
- view previous AI conversations
- view Git history
- manage settings

Clearly show:

```text
Offline
```

Do not pretend remote operations succeeded.

---

# 33. SECURITY

Security is critical.

Implement:

- Android Keystore
- encrypted sensitive storage
- secure credential handling
- certificate validation
- SSH host verification
- OAuth security
- API authentication
- session expiration
- secure logging
- no secrets in logs
- no credentials in crash reports
- clipboard warnings for secrets
- secure WebView configuration if WebViews are needed

Never store:

- SSH passwords
- API keys
- OAuth tokens
- private keys

in plaintext.

---

# 34. PERFORMANCE

The application must work on normal Android phones.

Avoid:

- blocking the main thread
- loading huge files into memory
- rendering huge terminal output at once
- sending entire repositories to AI
- unnecessary recompositions
- unnecessary network requests

Use:

- paging
- streaming
- caching
- background workers
- lazy lists
- incremental file loading

---

# 35. NETWORK ARCHITECTURE

Create a unified connection layer.

Support:

```text
HTTPS
SSH
SFTP
WebSocket
SSE
OAuth
```

Implement:

- retries
- exponential backoff
- cancellation
- connection state
- timeout
- reconnect
- offline detection

---

# 36. DATABASE

Use Room for persistent application data.

Entities should include concepts such as:

```text
Project
Server
Workspace
CredentialReference
Conversation
Message
AgentTask
ToolExecution
FileCache
GitRepository
Connector
Transfer
Activity
ProjectInstruction
```

Do not store sensitive secrets directly in Room.

Use secure references to Keystore-backed storage.

---

# 37. DESIGN SYSTEM

Create a consistent VMStudio Code design system.

Visual direction:

- professional
- modern
- developer-focused
- premium
- fast
- minimal
- technical
- readable

Avoid:

- excessive gradients
- childish UI
- oversized cards
- unnecessary animations
- clutter

Use Material 3 but customize the visual language.

Create reusable:

```text
VMButton
VMCard
VMDialog
VMTerminal
VMFileTree
VMCodeEditor
VMDiffViewer
VMAgentMessage
VMStatusBadge
VMProjectCard
VMServerCard
VMToolExecution
VMProgress
```

---

# 38. DARK MODE

Dark mode must be first-class.

Developer workspace should work especially well in dark mode.

Support:

- dark
- light
- system

Do not hardcode colors throughout the application.

Use theme tokens.

---

# 39. ACCESSIBILITY

Support:

- scalable text
- TalkBack
- content descriptions
- sufficient contrast
- keyboard navigation where applicable
- touch targets
- reduced animation

---

# 40. LOGGING

Create structured internal logging.

Categories:

```text
AUTH
SSH
SFTP
AI
AGENT
GIT
CONNECTOR
PROJECT
NETWORK
DATABASE
UI
SECURITY
```

Never log:

- passwords
- API keys
- tokens
- private keys

Create developer diagnostics that users can export without exposing secrets.

---

# 41. SETTINGS

Settings should contain:

```text
Account
AI
OmniRoute
Servers
Security
Git
Connectors
Editor
Terminal
Projects
Notifications
Appearance
Storage
Privacy
Diagnostics
About
```

---

# 42. OMNIROUTE HEALTH CHECK

Create:

```text
AI → OmniRoute → Connection Test
```

Show:

```text
Endpoint
Authentication
Latency
Available Models
Streaming
Status
```

Example:

```text
OmniRoute
● Connected

Endpoint:
https://ai.vmstudio.digital

Authentication:
✓ Valid

Models:
✓ Available

Streaming:
✓ Supported

Latency:
184 ms
```

Never expose sensitive authentication values.

---

# 43. AI MODEL ROUTING

Do not assume one model.

Allow OmniRoute to provide model selection.

The user should be able to choose:

```text
Fast
Balanced
Reasoning
Coding
Custom
```

If OmniRoute supports model metadata, dynamically retrieve it.

Do not hardcode model names unnecessarily.

---

# 44. AGENT EXECUTION ENGINE

Build the agent as an independent service.

Architecture:

```text
AgentController
      ↓
TaskPlanner
      ↓
ContextEngine
      ↓
ToolRegistry
      ↓
ToolExecutor
      ↓
PermissionManager
      ↓
ExecutionResult
      ↓
Validator
      ↓
AgentController
```

The agent must support loops:

```text
Plan
→ Execute
→ Observe
→ Validate
→ Correct
→ Continue
```

with limits for:

- maximum iterations
- maximum commands
- maximum token usage
- execution timeout

---

# 45. HUMAN APPROVAL

Implement approval checkpoints.

Example:

```text
AI wants to execute:

npm install

[Allow] [Deny]
```

For dangerous commands:

```text
WARNING

This command may delete production data:

DROP DATABASE ...

[Cancel]
[Execute]
```

Never bypass confirmation merely because AI requested it.

---

# 46. AI SAFETY BOUNDARIES

The AI agent must understand:

- project scope
- server scope
- permitted directories
- forbidden directories
- allowed commands
- dangerous commands
- production environment
- user permission level

Example:

```text
Project Root:
/var/www/example

AI Access:
✓ /var/www/example
✗ /etc
✗ /root
✗ other projects
```

---

# 47. MULTI-SERVER SUPPORT

A user may have multiple servers.

Example:

```text
Servers

Production
● Connected

Staging
● Connected

Development
○ Offline

Local
● Available
```

Never confuse project/server context.

Every terminal, AI task, file browser, and Git operation must know exactly which workspace it is operating against.

---

# 48. PROJECT CONTEXT BAR

Always make the active environment visible.

Example:

```text
VMStudio Code

Project:
TripCosmos

Server:
Production VPS

Path:
/var/www/tripcosmos

Branch:
main

AI:
OmniRoute / Coding Model

● Connected
```

This is critical to prevent accidental operations on the wrong project.

---

# 49. NOTIFICATIONS

Support notifications for:

- AI task completed
- build completed
- tests failed
- transfer completed
- server disconnected
- long-running task finished

Allow notification controls.

---

# 50. TESTING

Write real tests.

Minimum:

### Unit tests

- project manager
- SSH configuration
- permission engine
- AI provider
- agent planner
- context engine
- command safety
- database repositories

### Integration tests

- OmniRoute API
- SSH connection
- SFTP
- Git
- Google OAuth

### UI tests

- onboarding
- project creation
- server connection
- terminal
- editor
- AI chat
- permission dialog
- project switching

---

# 51. SECURITY TESTING

Test:

- credential leakage
- log leakage
- insecure storage
- invalid certificates
- SSH host changes
- expired OAuth
- API failures
- malicious command attempts
- path traversal
- unauthorized project access
- connector permission failures

---

# 52. DEVELOPMENT PHASES

Build in phases.

## Phase 1 — Foundation

Implement:

- project architecture
- navigation
- theme
- database
- settings
- security layer
- server manager

## Phase 2 — SSH/SFTP

Implement:

- SSH
- SFTP
- server connections
- remote filesystem
- terminal

## Phase 3 — Workspace

Implement:

- project explorer
- file editor
- tabs
- search
- file operations

## Phase 4 — Git

Implement complete Git workflow.

## Phase 5 — OmniRoute

Implement:

- authentication
- health check
- model discovery
- streaming
- conversations

## Phase 6 — AI Agent

Implement:

- tools
- planner
- context engine
- execution loop
- approvals
- diff review

## Phase 7 — Connectors

Implement Google Drive and connector framework.

## Phase 8 — Advanced Workspace

Implement:

- tasks
- activity
- server dashboard
- transfer manager
- notifications

## Phase 9 — Hardening

Perform:

- security audit
- performance audit
- memory audit
- network audit
- UX audit
- automated testing

---

# 53. IMPORTANT DEVELOPMENT RULES

Do NOT:

- create fake APIs
- create fake SSH functionality
- create fake AI responses
- hardcode credentials
- hardcode project data
- use placeholder screens as final implementation
- leave TODOs for core functionality
- silently ignore errors
- create disconnected UI
- duplicate business logic
- store secrets in plaintext
- assume root access
- assume bash exists
- assume internet connectivity
- send entire repositories to the AI unnecessarily

When an external dependency is required, select a maintained Android-compatible implementation and document the choice.

---

# 54. CODE QUALITY

Write production-quality code.

Requirements:

- meaningful names
- small focused classes
- interfaces for replaceable systems
- dependency injection
- coroutine cancellation
- lifecycle awareness
- structured errors
- testable code
- no unnecessary global state
- no memory leaks
- no blocking operations on UI thread

Use comments only where they explain important decisions.

---

# 55. DOCUMENTATION

Create:

```text
README.md
ARCHITECTURE.md
SECURITY.md
OMNIROUTE.md
SSH.md
SFTP.md
AI-AGENT.md
CONNECTORS.md
TESTING.md
DEVELOPMENT.md
```

Document:

- architecture
- setup
- build
- configuration
- OmniRoute integration
- authentication
- security
- testing
- release process

---

# 56. FINAL QUALITY GATE

Before considering VMStudio Code complete, perform a complete audit.

Check:

### Architecture
- modular
- scalable
- maintainable

### Android
- lifecycle safe
- responsive
- tablet compatible
- background operations safe

### SSH
- secure
- reconnectable
- host verification

### SFTP
- reliable
- resumable where practical
- transfer queue

### Terminal
- interactive
- stable
- multiple sessions

### Editor
- usable for real coding

### Git
- complete workflow

### AI
- OmniRoute working
- streaming working
- tools working
- permissions working
- context management working

### Agent
- plan
- execute
- observe
- validate
- correct

### Security
- no secret leakage
- secure credentials
- safe command execution

### UX
- clear project context
- clear server context
- clear AI activity
- clear errors
- clear approvals

### Performance
- no UI blocking
- reasonable memory usage
- efficient network usage

---

# 57. DEFINITION OF DONE

VMStudio Code is NOT complete when screens exist.

It is complete when a real developer can perform this workflow:

```text
Install VMStudio Code
        ↓
Add SSH server
        ↓
Connect successfully
        ↓
Browse remote project
        ↓
Open project
        ↓
Inspect code
        ↓
Open terminal
        ↓
Run commands
        ↓
Use Git
        ↓
Ask AI:
"Add feature X"
        ↓
AI understands project
        ↓
AI creates a plan
        ↓
User approves
        ↓
AI reads relevant files
        ↓
AI modifies files
        ↓
AI runs tests
        ↓
AI detects errors
        ↓
AI fixes errors
        ↓
User reviews diff
        ↓
Commit
        ↓
Push
        ↓
Task completed
```

This complete workflow must actually work.

---

# 58. FIRST EXECUTION IN ANDROID STUDIO

Before writing large amounts of code:

1. Inspect the existing Android project.
2. Determine current package/application ID.
3. Inspect Gradle configuration.
4. Inspect Android SDK configuration.
5. Inspect existing dependencies.
6. Create architecture plan.
7. Identify incompatible dependencies.
8. Establish module structure.
9. Establish navigation architecture.
10. Establish secure storage architecture.
11. Establish SSH/SFTP abstraction.
12. Establish AI provider abstraction.
13. Establish project/workspace model.
14. Establish database schema.

Then implement incrementally.

After each major phase:

- compile
- run tests
- inspect errors
- fix issues
- verify functionality

Do not continue building on top of broken code.

---

# 59. FINAL INSTRUCTION

Think like the engineering team behind a professional developer product.

Do not optimize for the number of screens.

Optimize for:

**reliability + security + developer productivity + extensibility + excellent UX.**

VMStudio Code must feel like a real developer tool, not a chatbot wrapped inside an Android application.

The most important architectural principle is:

> **AI is an intelligent operator inside the development environment — not the development environment itself.**

The Android application owns the workspace, projects, files, SSH, SFTP, terminal, Git, connectors, permissions, security, and execution environment.

OmniRoute provides the intelligence layer.

The agent orchestrates the two.

Build the system accordingly.