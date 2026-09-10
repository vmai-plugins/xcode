package digital.vmstudio.code.core.project.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import digital.vmstudio.code.core.project.DefaultProjectRepository
import digital.vmstudio.code.core.project.ProjectRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProjectModule {

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: DefaultProjectRepository): ProjectRepository
}
