package digital.vmstudio.code.core.ai.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import digital.vmstudio.code.core.ai.claudecode.ClaudeCodeStreamParser
import digital.vmstudio.code.core.ai.repository.AgentConversationRepository
import digital.vmstudio.code.core.ai.repository.AgentTaskRepository
import digital.vmstudio.code.core.ai.repository.DefaultAgentConversationRepository
import digital.vmstudio.code.core.ai.repository.DefaultAgentTaskRepository
import javax.inject.Singleton

/**
 * No direct `AiProvider` binding: which backend runs a request depends on the user's
 * choice, so callers take `AiProviderRegistry` and resolve per request. Binding one
 * provider as *the* provider is what would let a preference silently do nothing.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindAgentConversationRepository(
        impl: DefaultAgentConversationRepository,
    ): AgentConversationRepository

    @Binds
    @Singleton
    abstract fun bindAgentTaskRepository(
        impl: DefaultAgentTaskRepository,
    ): AgentTaskRepository

    companion object {
        @Provides
        @Singleton
        fun provideClaudeCodeStreamParser(): ClaudeCodeStreamParser = ClaudeCodeStreamParser()
    }
}
