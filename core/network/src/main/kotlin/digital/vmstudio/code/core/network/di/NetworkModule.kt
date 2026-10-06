package digital.vmstudio.code.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // A model can think for a long time before its first token, and a streaming
        // response has long gaps between chunks. A conventional 30s read timeout
        // would cancel legitimate generations mid-answer.
        .readTimeout(READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // OkHttp's own retry only covers connection-level failures; request retry is
        // handled by VmHttpClient, which knows which status codes deserve it.
        .retryOnConnectionFailure(true)
        // OkHttp drops Authorization when a redirect leaves the host, but not custom
        // key headers such as x-api-key: a gateway (or a proxy in front of it) that
        // redirected elsewhere would have handed the key to that other host.
        .addInterceptor { chain ->
            val request = chain.request()
            if (KEY_HEADERS.none { request.header(it) != null }) return@addInterceptor chain.proceed(request)
            chain.proceed(request.newBuilder().tag(KeyOwner::class.java, KeyOwner(request.url.host)).build())
        }
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val owner = request.tag(KeyOwner::class.java)
            if (owner == null || owner.host == request.url.host) {
                return@addNetworkInterceptor chain.proceed(request)
            }
            val stripped = request.newBuilder()
            KEY_HEADERS.forEach { stripped.removeHeader(it) }
            chain.proceed(stripped.build())
        }
        // No logging interceptor, at any level. It would write Authorization headers
        // and full request bodies into logcat, which is exactly what the redaction
        // layer exists to prevent.
        .build()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    /** The host a request's credentials were meant for; follow-up requests keep the tag. */
    private class KeyOwner(val host: String)

    private val KEY_HEADERS = listOf("Authorization", "x-api-key", "api-key")

    private const val CONNECT_TIMEOUT_SECONDS = 20L
    private const val READ_TIMEOUT_MINUTES = 10L
    private const val WRITE_TIMEOUT_SECONDS = 30L
}
