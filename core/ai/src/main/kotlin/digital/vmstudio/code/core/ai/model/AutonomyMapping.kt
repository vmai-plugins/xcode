package digital.vmstudio.code.core.ai.model

import digital.vmstudio.code.core.common.preferences.AgentAutonomyLevel

/**
 * Maps the user's autonomy preference onto the CLI's permission mode.
 *
 * These were two separate vocabularies for one idea: a level chosen in Settings and a
 * mode chosen per run in the chat. Only the latter reached the agent, so the Settings
 * control stored a value that changed nothing. This is the single place the two meet
 * — Settings supplies the default, the chat overrides it for one run, and both end up
 * as the same `--permission-mode` flag.
 *
 * The mapping is deliberately conservative at the safe end: [AgentAutonomyLevel.SAFE_AUTO]
 * becomes `plan`, which proposes without changing anything, rather than a mode that
 * edits files on the user's behalf.
 */
fun AgentAutonomyLevel.toPermissionMode(): AgentPermissionMode = when (this) {
    // Every tool call is proposed and awaits approval.
    AgentAutonomyLevel.ASK_EVERY_TIME -> AgentPermissionMode.MANUAL

    // Read-only inspection is automatic; anything with an effect is asked. Plan mode
    // is the closest honest equivalent the CLI offers: it never writes.
    AgentAutonomyLevel.SAFE_AUTO -> AgentPermissionMode.PLAN

    // File edits run automatically; other tools still prompt.
    AgentAutonomyLevel.DEVELOPER -> AgentPermissionMode.ACCEPT_EDITS

    // The full loop, without prompting.
    AgentAutonomyLevel.FULL_AGENT -> AgentPermissionMode.BYPASS
}

/** The level a mode corresponds to, for showing the two controls in agreement. */
fun AgentPermissionMode.toAutonomyLevel(): AgentAutonomyLevel = when (this) {
    AgentPermissionMode.MANUAL -> AgentAutonomyLevel.ASK_EVERY_TIME
    AgentPermissionMode.PLAN -> AgentAutonomyLevel.SAFE_AUTO
    AgentPermissionMode.ACCEPT_EDITS -> AgentAutonomyLevel.DEVELOPER
    AgentPermissionMode.BYPASS -> AgentAutonomyLevel.FULL_AGENT
}
