package digital.vmstudio.code.core.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.vmCatching
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

/** Downloads a release APK into app-private storage, ready for [ApkInstaller]. */
@Singleton
class ApkDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Downloads [url] to a file named [fileName] under a cache subdirectory,
     * reporting fractional progress as bytes arrive.
     *
     * Writes to a `.part` file first and moves it into place atomically, so a
     * connection dropped mid-download never leaves a truncated APK where
     * [ApkInstaller] expects a complete one.
     */
    suspend fun download(
        url: String,
        fileName: String,
        onProgress: (Float) -> Unit,
    ): VmResult<File> = withContext(ioDispatcher) {
        vmCatching(
            mapper = {
                VmError.Network(
                    summary = "Update download failed",
                    reason = it.message,
                    cause = it,
                )
            },
        ) {
            runInterruptible {
                val dir = File(context.cacheDir, UPDATES_DIR).apply { mkdirs() }
                val destFile = File(dir, fileName)
                val partFile = File(dir, "$fileName.part")

                val response = client.newCall(Request.Builder().url(url).build()).execute()
                response.use {
                    if (!it.isSuccessful) throw IOException("Download failed: HTTP ${it.code}")
                    val body = it.body ?: throw IOException("Empty download response")
                    val total = body.contentLength()

                    body.byteStream().use { input ->
                        partFile.outputStream().use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var downloaded = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }

                Files.move(partFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                destFile
            }
        }
    }

    private companion object {
        const val UPDATES_DIR = "updates"
        const val BUFFER_SIZE = 8 * 1024
    }
}
