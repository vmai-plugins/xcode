package digital.vmstudio.code.core.database.repository
 
import digital.vmstudio.code.core.database.dao.ActivityDao
import digital.vmstudio.code.core.database.entity.ActivityCategory
import digital.vmstudio.code.core.database.entity.ActivityEntity
import digital.vmstudio.code.core.database.entity.ActivitySeverity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRepository @Inject constructor(
    private val activityDao: ActivityDao,
) {
    fun observeRecent(limit: Int = 100): Flow<List<ActivityEntity>> =
        activityDao.observeRecent(limit)

    suspend fun record(
        category: ActivityCategory,
        title: String,
        detail: String? = null,
        projectId: String? = null,
        serverId: String? = null,
        taskId: String? = null,
        severity: ActivitySeverity = ActivitySeverity.INFO,
    ) {
        val entity = ActivityEntity(
            id = UUID.randomUUID().toString(),
            timestampMillis = System.currentTimeMillis(),
            category = category,
            severity = severity,
            title = title,
            detail = detail,
            projectId = projectId,
            serverId = serverId,
            taskId = taskId,
        )
        activityDao.insert(entity)
    }

    suspend fun clearAll() {
        activityDao.clear()
    }
}
