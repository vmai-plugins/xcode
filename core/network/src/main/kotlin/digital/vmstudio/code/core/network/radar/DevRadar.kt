package digital.vmstudio.code.core.network.radar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

enum class ServiceCategory {
    SSH,
    AI_INFERENCE,
    WIRELESS_ADB,
    WEB_DEV,
    CUSTOM
}

data class DiscoveredPort(
    val port: Int,
    val serviceName: String,
    val category: ServiceCategory,
    val latencyMs: Long
)

data class DiscoveredHost(
    val ip: String,
    val hostname: String? = null,
    val openPorts: List<DiscoveredPort> = emptyList(),
    val isLocalDevice: Boolean = false
) {
    val isComputeNode: Boolean get() = openPorts.any { it.category == ServiceCategory.SSH || it.category == ServiceCategory.AI_INFERENCE }
}

@Singleton
class DevRadar @Inject constructor() {

    companion object {
        val KEY_DEV_PORTS = mapOf(
            22 to ("OpenSSH Server" to ServiceCategory.SSH),
            11434 to ("Ollama AI Engine" to ServiceCategory.AI_INFERENCE),
            5555 to ("Wireless ADB Debugging" to ServiceCategory.WIRELESS_ADB),
            3000 to ("Web Dev Server (Node/React/Next)" to ServiceCategory.WEB_DEV),
            5173 to ("Vite Dev Server" to ServiceCategory.WEB_DEV),
            8080 to ("HTTP Web Server" to ServiceCategory.WEB_DEV),
            8000 to ("FastAPI / Python Server" to ServiceCategory.WEB_DEV),
            80 to ("HTTP Web" to ServiceCategory.WEB_DEV)
        )
    }

    /**
     * Resolves the current local network subnet IP addresses (e.g., 192.168.29.0/24 or Tailscale).
     */
    fun getLocalIpAddresses(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (iface in interfaces) {
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) {
                        addr.hostAddress?.let { ips.add(it) }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignored, fallback gracefully
        }
        return ips
    }

    /**
     * Probes a specific IP and port with high-speed socket connection.
     */
    suspend fun probePort(ip: String, port: Int, timeoutMs: Int = 200): DiscoveredPort? = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                val latency = System.currentTimeMillis() - start
                val (serviceName, category) = KEY_DEV_PORTS[port] ?: ("Service on port $port" to ServiceCategory.CUSTOM)
                DiscoveredPort(port, serviceName, category, latency)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Scans a single IP for developer ports.
     */
    suspend fun scanHost(ip: String, ports: List<Int> = KEY_DEV_PORTS.keys.toList()): DiscoveredHost? = coroutineScope {
        val openPorts = ports.map { port ->
            async { probePort(ip, port) }
        }.awaitAll().filterNotNull()

        if (openPorts.isNotEmpty()) {
            DiscoveredHost(
                ip = ip,
                hostname = runCatching { InetAddress.getByName(ip).hostName }.getOrNull(),
                openPorts = openPorts
            )
        } else {
            null
        }
    }

    /**
     * Scans the subnet in batches.
     */
    suspend fun scanSubnet(
        baseIp: String? = null,
        onHostDiscovered: (DiscoveredHost) -> Unit = {}
    ): List<DiscoveredHost> = withContext(Dispatchers.IO) {
        val localIps = getLocalIpAddresses()
        val primaryIp = baseIp ?: localIps.firstOrNull { it.startsWith("192.168.") || it.startsWith("10.") || it.startsWith("172.") } ?: "192.168.1.1"
        val prefix = primaryIp.substringBeforeLast(".")

        val discoveredHosts = mutableListOf<DiscoveredHost>()
        val candidates = (1..254).map { "$prefix.$it" }

        // Chunk candidates to prevent thread pool starvation
        candidates.chunked(32).forEach { chunk ->
            coroutineScope {
                val results = chunk.map { ip ->
                    async {
                        val host = scanHost(ip, listOf(22, 11434, 5555, 3000, 5173))
                        if (host != null) {
                            withContext(Dispatchers.Main) {
                                onHostDiscovered(host)
                            }
                            host
                        } else null
                    }
                }.awaitAll().filterNotNull()
                discoveredHosts.addAll(results)
            }
        }

        discoveredHosts
    }

    /**
     * Generates connection automation code based on discovered service.
     */
    fun generateConnectCode(host: DiscoveredHost, port: DiscoveredPort, username: String = "tripc"): String {
        return when (port.category) {
            ServiceCategory.SSH -> """
                # ⚡ 1-Click SSH Connect
                ssh $username@${host.ip} -p ${port.port}

                # 🔄 Reverse Dev Port Forwarding (view remote port on phone)
                ssh -R 3000:localhost:3000 $username@${host.ip} -N
            """.trimIndent()

            ServiceCategory.AI_INFERENCE -> """
                # 🤖 Connect to Remote Ollama Engine
                export OLLAMA_HOST=http://${host.ip}:${port.port}
                curl http://${host.ip}:${port.port}/api/tags

                # Query Remote AI Model
                curl http://${host.ip}:${port.port}/api/generate -d '{"model": "qwen2.5-coder", "prompt": "Write a test"}'
            """.trimIndent()

            ServiceCategory.WIRELESS_ADB -> """
                # 📲 Connect Wireless ADB Debugging
                adb connect ${host.ip}:${port.port}
                adb devices
            """.trimIndent()

            ServiceCategory.WEB_DEV -> """
                # 🌐 Web Dev Server URL
                http://${host.ip}:${port.port}

                # Forward to Phone Browser
                ssh -L ${port.port}:localhost:${port.port} $username@${host.ip}
            """.trimIndent()

            ServiceCategory.CUSTOM -> """
                # Generic Socket Connect
                nc -vz ${host.ip} ${port.port}
            """.trimIndent()
        }
    }
}
