package digital.vmstudio.code.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import digital.vmstudio.code.background.AgentRunService
import digital.vmstudio.code.core.ai.background.AgentExecutionKeepAlive
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AgentExecutionModule {

    @Provides
    @Singleton
    fun provideAgentExecutionKeepAlive(
        @ApplicationContext context: Context,
    ): AgentExecutionKeepAlive = object : AgentExecutionKeepAlive {
        override fun onExecutionStarted() {
            AgentRunService.startExecution(context)
        }

        override fun onExecutionStopped() {
            AgentRunService.stopExecution(context)
        }
    }
}
