package digital.vmstudio.code.core.update.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import digital.vmstudio.code.core.update.GitHubUpdateChecker
import digital.vmstudio.code.core.update.UpdateChecker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {

    @Binds
    @Singleton
    abstract fun bindUpdateChecker(impl: GitHubUpdateChecker): UpdateChecker
}
