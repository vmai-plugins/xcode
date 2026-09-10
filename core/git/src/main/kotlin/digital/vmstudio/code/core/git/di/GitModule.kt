package digital.vmstudio.code.core.git.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import digital.vmstudio.code.core.git.CommandGitService
import digital.vmstudio.code.core.git.GitService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GitModule {

    @Binds
    @Singleton
    abstract fun bindGitService(impl: CommandGitService): GitService
}
