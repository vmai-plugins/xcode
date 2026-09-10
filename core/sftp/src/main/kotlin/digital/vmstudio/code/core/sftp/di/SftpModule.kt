package digital.vmstudio.code.core.sftp.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import digital.vmstudio.code.core.sftp.fs.RemoteFileSystem
import digital.vmstudio.code.core.sftp.fs.SftpRemoteFileSystem
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SftpModule {

    @Binds
    @Singleton
    abstract fun bindRemoteFileSystem(impl: SftpRemoteFileSystem): RemoteFileSystem
}
