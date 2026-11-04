package digital.vmstudio.code.core.sftp.fs

import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class ShadowCacheEntry(
    val content: String,
    val timestamp: Long,
    val remoteMtimeSeconds: Long = 0L,
    val sizeBytes: Long = 0L,
)

/**
 * High-speed Phone RAM & Storage Shadow Cache.
 *
 * Utilizes available mobile memory to cache remote files read during AI reasoning loops
 * and project navigation. Repeated file reads are served in < 1ms from phone memory,
 * dramatically lowering network latency over SSH/SFTP/Tailscale and saving battery.
 *
 * Automatically invalidated on file write, delete, or rename.
 */
@Singleton
class PhoneShadowCache @Inject constructor() {

    private val cache = ConcurrentHashMap<String, ShadowCacheEntry>()
    private val hits = AtomicLong(0L)
    private val misses = AtomicLong(0L)

    private fun key(serverId: String, path: String): String = "$serverId:${path.trim().replace('\\', '/')}"

    /**
     * Attempts to fetch a cached version if it was cached within [freshnessWindowMillis],
     * or if its size and remote mtime match.
     */
    fun get(
        serverId: String,
        path: String,
        freshnessWindowMillis: Long = DEFAULT_FRESHNESS_MILLIS,
    ): String? {
        val entry = cache[key(serverId, path)]
        if (entry != null) {
            val age = System.currentTimeMillis() - entry.timestamp
            if (age <= freshnessWindowMillis) {
                val hitCount = hits.incrementAndGet()
                VmLog.d(LogCategory.SFTP, TAG, "Phone Shadow Cache HIT (#$hitCount): $path (age: ${age}ms)")
                return entry.content
            }
        }
        misses.incrementAndGet()
        return null
    }

    /**
     * Returns cached content if remote mtime and size match known cached values.
     */
    fun getIfMatch(
        serverId: String,
        path: String,
        remoteMtimeSeconds: Long,
        sizeBytes: Long,
    ): String? {
        val entry = cache[key(serverId, path)]
        if (entry != null && entry.remoteMtimeSeconds > 0L &&
            entry.remoteMtimeSeconds == remoteMtimeSeconds && entry.sizeBytes == sizeBytes
        ) {
            val hitCount = hits.incrementAndGet()
            VmLog.d(LogCategory.SFTP, TAG, "Phone Shadow Cache HIT via mtime (#$hitCount): $path")
            return entry.content
        }
        return get(serverId, path)
    }

    /**
     * Caches file content in phone RAM.
     */
    fun put(
        serverId: String,
        path: String,
        content: String,
        remoteMtimeSeconds: Long = 0L,
    ) {
        if (cache.size >= MAX_ENTRIES) {
            // Evict oldest 20% entries when memory limit is reached
            val oldest = cache.entries.sortedBy { it.value.timestamp }.take(MAX_ENTRIES / 5)
            oldest.forEach { cache.remove(it.key) }
        }
        cache[key(serverId, path)] = ShadowCacheEntry(
            content = content,
            timestamp = System.currentTimeMillis(),
            remoteMtimeSeconds = remoteMtimeSeconds,
            sizeBytes = content.length.toLong(),
        )
    }

    /**
     * Invalidates a single cached file path.
     */
    fun invalidate(serverId: String, path: String) {
        val removed = cache.remove(key(serverId, path))
        if (removed != null) {
            VmLog.d(LogCategory.SFTP, TAG, "Invalidated shadow cache for $path")
        }
    }

    /**
     * Invalidates all files under a directory prefix or for a given server.
     */
    fun invalidatePrefix(serverId: String, dirPrefix: String) {
        val prefix = "$serverId:${dirPrefix.trimEnd('/')}/"
        val keysToRemove = cache.keys.filter { it.startsWith(prefix) }
        keysToRemove.forEach { cache.remove(it) }
    }

    fun clear() {
        cache.clear()
        hits.set(0L)
        misses.set(0L)
    }

    fun stats(): Map<String, Long> = mapOf(
        "cachedFiles" to cache.size.toLong(),
        "hits" to hits.get(),
        "misses" to misses.get(),
    )

    companion object {
        private const val TAG = "PhoneShadowCache"
        private const val MAX_ENTRIES = 256
        private const val DEFAULT_FRESHNESS_MILLIS = 90_000L // 90 seconds
    }
}
