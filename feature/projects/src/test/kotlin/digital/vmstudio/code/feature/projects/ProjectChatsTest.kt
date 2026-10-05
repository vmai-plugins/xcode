package digital.vmstudio.code.feature.projects

import digital.vmstudio.code.core.ai.repository.AgentConversation
import digital.vmstudio.code.core.project.Project
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectChatsTest {

    private val project = Project(
        id = "p1",
        name = "Shop",
        description = "",
        serverId = "srv",
        remotePath = "/home/shop",
        projectTypeId = "generic",
        isFavorite = false,
        lastOpenedAtMillis = 0L,
    )

    private fun chat(serverId: String?, folder: String?) = AgentConversation(
        id = "c",
        title = "t",
        providerId = "omniroute",
        providerSessionId = null,
        serverId = serverId,
        workingDirectory = folder,
        promptTokens = 0,
        completionTokens = 0,
        updatedAtMillis = 0,
    )

    @Test
    fun `a chat in the project's folder on its server belongs to it`() {
        assertTrue(chat("srv", "/home/shop").belongsTo(project))
        assertTrue(chat("srv", "/home/shop/").belongsTo(project))
    }

    @Test
    fun `other folders, subfolders and servers do not`() {
        assertFalse(chat("srv", "/home/shop-old").belongsTo(project))
        assertFalse(chat("srv", "/home/shop/api").belongsTo(project))
        assertFalse(chat("other", "/home/shop").belongsTo(project))
        assertFalse(chat(null, "/home/shop").belongsTo(project))
    }
}
