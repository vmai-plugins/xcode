package digital.vmstudio.code.core.security.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import digital.vmstudio.code.core.security.crypto.AndroidKeystoreCrypto
import digital.vmstudio.code.core.security.crypto.KeystoreCrypto
import digital.vmstudio.code.core.security.store.FileSecureCredentialStore
import digital.vmstudio.code.core.security.store.SecureCredentialStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindKeystoreCrypto(impl: AndroidKeystoreCrypto): KeystoreCrypto

    @Binds
    @Singleton
    abstract fun bindSecureCredentialStore(impl: FileSecureCredentialStore): SecureCredentialStore
}
