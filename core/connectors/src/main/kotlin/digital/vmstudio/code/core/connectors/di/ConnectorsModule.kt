package digital.vmstudio.code.core.connectors.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import digital.vmstudio.code.core.connectors.api.ProjectsHubClient
import digital.vmstudio.code.core.connectors.sync.ProjectsHubSyncManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ConnectorsModule {
    // Both ProjectsHubClient and ProjectsHubSyncManager use @Inject constructor with @Singleton.
    // Additional connector providers can be registered here as needed.
}
