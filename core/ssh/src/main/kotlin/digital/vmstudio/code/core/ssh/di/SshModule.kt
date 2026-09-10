package digital.vmstudio.code.core.ssh.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import digital.vmstudio.code.core.ssh.command.CommandApprovalGate
import digital.vmstudio.code.core.ssh.command.CommandGuard
import digital.vmstudio.code.core.ssh.command.DefaultCommandApprovalGate
import digital.vmstudio.code.core.ssh.command.DefaultCommandGuard
import digital.vmstudio.code.core.ssh.connection.DefaultSshConnectionManager
import digital.vmstudio.code.core.ssh.connection.SshConnectionManager
import digital.vmstudio.code.core.ssh.host.DefaultHostKeyRepository
import digital.vmstudio.code.core.ssh.host.HostKeyRepository
import digital.vmstudio.code.core.ssh.recipes.DefaultRecipeExecutor
import digital.vmstudio.code.core.ssh.recipes.RecipeExecutor
import digital.vmstudio.code.core.ssh.repository.DefaultServerRepository
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SshModule {

    @Binds
    @Singleton
    abstract fun bindServerRepository(impl: DefaultServerRepository): ServerRepository

    @Binds
    @Singleton
    abstract fun bindHostKeyRepository(impl: DefaultHostKeyRepository): HostKeyRepository

    @Binds
    @Singleton
    abstract fun bindSshConnectionManager(
        impl: DefaultSshConnectionManager,
    ): SshConnectionManager

    @Binds
    @Singleton
    abstract fun bindCommandApprovalGate(
        impl: DefaultCommandApprovalGate,
    ): CommandApprovalGate

    @Binds
    @Singleton
    abstract fun bindCommandGuard(impl: DefaultCommandGuard): CommandGuard

    @Binds
    @Singleton
    abstract fun bindRecipeExecutor(impl: DefaultRecipeExecutor): RecipeExecutor
}
