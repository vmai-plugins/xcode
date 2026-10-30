package digital.vmstudio.code.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import digital.vmstudio.code.core.database.VmDatabase
import digital.vmstudio.code.core.database.dao.ActivityDao
import digital.vmstudio.code.core.database.dao.AgentTaskDao
import digital.vmstudio.code.core.database.dao.ConnectorDao
import digital.vmstudio.code.core.database.dao.ConversationDao
import digital.vmstudio.code.core.database.dao.CredentialReferenceDao
import digital.vmstudio.code.core.database.dao.FileCacheDao
import digital.vmstudio.code.core.database.dao.GitRepositoryDao
import digital.vmstudio.code.core.database.dao.KnownHostKeyDao
import digital.vmstudio.code.core.database.dao.MessageDao
import digital.vmstudio.code.core.database.dao.ProjectCommandDao
import digital.vmstudio.code.core.database.dao.ProjectDao
import digital.vmstudio.code.core.database.dao.ProjectEnvVarDao
import digital.vmstudio.code.core.database.dao.ProjectInstructionDao
import digital.vmstudio.code.core.database.dao.RecentFileDao
import digital.vmstudio.code.core.database.dao.ServerDao
import digital.vmstudio.code.core.database.dao.ToolExecutionDao
import digital.vmstudio.code.core.database.dao.TransferDao
import digital.vmstudio.code.core.database.dao.WorkspaceDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VmDatabase =
        Room.databaseBuilder(context, VmDatabase::class.java, VmDatabase.NAME)
            // Foreign keys are declared on the entities; SQLite only enforces them
            // when explicitly enabled per-connection. In WAL mode, reader pool connections
            // bypass onOpen, so onConfigure is required to enforce constraints globally.
            .openHelperFactory(
                object : androidx.sqlite.db.SupportSQLiteOpenHelper.Factory {
                    private val delegate = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()

                    override fun create(
                        configuration: androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration,
                    ): androidx.sqlite.db.SupportSQLiteOpenHelper {
                        val newCallback = object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(configuration.callback.version) {
                            override fun onConfigure(db: SupportSQLiteDatabase) {
                                db.setForeignKeyConstraintsEnabled(true)
                                configuration.callback.onConfigure(db)
                            }

                            override fun onCreate(db: SupportSQLiteDatabase) = configuration.callback.onCreate(db)

                            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                                configuration.callback.onUpgrade(db, oldVersion, newVersion)

                            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                                configuration.callback.onDowngrade(db, oldVersion, newVersion)

                            override fun onOpen(db: SupportSQLiteDatabase) = configuration.callback.onOpen(db)

                            override fun onCorruption(db: SupportSQLiteDatabase) = configuration.callback.onCorruption(db)
                        }

                        val newConfig = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                            .name(configuration.name)
                            .callback(newCallback)
                            .noBackupDirectory(configuration.useNoBackupDirectory)
                            .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery)
                            .build()

                        return delegate.create(newConfig)
                    }
                },
            )
            .addCallback(
                object : androidx.room.RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                },
            )
            .fallbackToDestructiveMigrationOnDowngrade(true)
            // No fallbackToDestructiveMigration: losing a user's servers and
            // projects on upgrade is never an acceptable outcome. Every schema
            // change ships a real migration.
            .build()

    @Provides fun provideServerDao(db: VmDatabase): ServerDao = db.serverDao()

    @Provides fun provideKnownHostKeyDao(db: VmDatabase): KnownHostKeyDao = db.knownHostKeyDao()

    @Provides fun provideProjectDao(db: VmDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideProjectCommandDao(db: VmDatabase): ProjectCommandDao = db.projectCommandDao()

    @Provides
    fun provideProjectInstructionDao(db: VmDatabase): ProjectInstructionDao =
        db.projectInstructionDao()

    @Provides
    fun provideProjectEnvVarDao(db: VmDatabase): ProjectEnvVarDao = db.projectEnvVarDao()

    @Provides fun provideRecentFileDao(db: VmDatabase): RecentFileDao = db.recentFileDao()

    @Provides fun provideFileCacheDao(db: VmDatabase): FileCacheDao = db.fileCacheDao()

    @Provides fun provideConversationDao(db: VmDatabase): ConversationDao = db.conversationDao()

    @Provides fun provideMessageDao(db: VmDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideToolExecutionDao(db: VmDatabase): ToolExecutionDao = db.toolExecutionDao()

    @Provides fun provideAgentTaskDao(db: VmDatabase): AgentTaskDao = db.agentTaskDao()

    @Provides
    fun provideGitRepositoryDao(db: VmDatabase): GitRepositoryDao = db.gitRepositoryDao()

    @Provides fun provideConnectorDao(db: VmDatabase): ConnectorDao = db.connectorDao()

    @Provides fun provideTransferDao(db: VmDatabase): TransferDao = db.transferDao()

    @Provides fun provideActivityDao(db: VmDatabase): ActivityDao = db.activityDao()

    @Provides
    fun provideCredentialReferenceDao(db: VmDatabase): CredentialReferenceDao =
        db.credentialReferenceDao()

    @Provides fun provideWorkspaceDao(db: VmDatabase): WorkspaceDao = db.workspaceDao()
}
