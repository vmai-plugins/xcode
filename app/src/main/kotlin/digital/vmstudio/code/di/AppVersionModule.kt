package digital.vmstudio.code.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import digital.vmstudio.code.BuildConfig
import digital.vmstudio.code.core.common.version.AppVersionProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppVersionModule {

    @Provides
    @Singleton
    fun provideAppVersionProvider(): AppVersionProvider = object : AppVersionProvider {
        override val versionName: String = BuildConfig.VERSION_NAME
    }
}
