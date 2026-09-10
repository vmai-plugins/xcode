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

    private const val CONNECT_TIMEOUT_SECONDS = 20L
    private const val READ_TIMEOUT_MINUTES = 10L
    private const val WRITE_TIMEOUT_SECONDS = 30L
}
