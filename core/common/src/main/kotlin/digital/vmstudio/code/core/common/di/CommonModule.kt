package digital.vmstudio.code.core.common.di

import digital.vmstudio.code.core.common.net.ConnectivityNetworkMonitor
import digital.vmstudio.code.core.common.net.NetworkMonitor
import digital.vmstudio.code.core.common.preferences.UserPreferencesRepository
import digital.vmstudio.code.core.common.preferences.UserPreferencesSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CommonModule {

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor

    @Binds
    @Singleton
    abstract fun bindUserPreferencesSource(
        impl: UserPreferencesRepository,
    ): UserPreferencesSource
}
