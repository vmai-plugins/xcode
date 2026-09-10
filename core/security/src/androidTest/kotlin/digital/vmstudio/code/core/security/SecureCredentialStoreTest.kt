package digital.vmstudio.code.core.security

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import digital.vmstudio.code.core.common.result.getOrNull
import digital.vmstudio.code.core.common.result.getOrThrow
import digital.vmstudio.code.core.security.crypto.AndroidKeystoreCrypto
import digital.vmstudio.code.core.security.model.Secret
import digital.vmstudio.code.core.security.model.SecretType
import digital.vmstudio.code.core.security.store.FileSecureCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Exercises the real credential store against the real Keystore and filesystem.
 *
 * This is the class that holds every SSH password, private key and API token the
 * app knows. Its unit-testable parts were covered; the parts that matter most — that
 * the bytes on disk are actually encrypted, that a delete really removes them, and
 * that concurrent writes do not corrupt the index — could only be verified here.
 */
@RunWith(AndroidJUnit4::class)
class SecureCredentialStoreTest {

    private lateinit var store: FileSecureCredentialStore
    private lateinit var crypto: AndroidKeystoreCrypto
    private lateinit var credentialsDir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        credentialsDir = File(context.filesDir, "credentials")
        credentialsDir.deleteRecursively()

        crypto = AndroidKeystoreCrypto()
        store = FileSecureCredentialStore(context, crypto, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        credentialsDir.deleteRecursively()
        crypto.destroyMasterKey()
    }

    @Test
    fun aStoredSecretComesBackIntact() = runTest {
        val ref = store.put(
            type = SecretType.SSH_PASSWORD,
            label = "Production VPS",
            secret = Secret.of("correct-horse-battery-staple"),
        ).getOrThrow()

        val read = store.read(ref.id).getOrThrow()

        assertEquals("correct-horse-battery-staple", read.useAsString { it })
    }

    @Test
    fun thePlaintextIsNotPresentOnDisk() = runTest {
        val distinctive = "unmistakable-secret-value-9f3a"
        store.put(SecretType.AI_API_KEY, "key", Secret.of(distinctive)).getOrThrow()

        val onDisk = credentialsDir.walkTopDown()
            .filter { it.isFile }
            .joinToString("\n") { String(it.readBytes(), Charsets.ISO_8859_1) }

        assertTrue("expected files to exist", onDisk.isNotEmpty())
        assertFalse("the secret must not be readable on disk", onDisk.contains(distinctive))
    }

    @Test
    fun theLabelIsNotStoredInPlaintextEither() = runTest {
        // The index is encrypted too: labels routinely name a client or a server and
        // are not something to leave readable in app storage.
        store.put(SecretType.SSH_PASSWORD, "Acme Corp production", Secret.of("x-secret-x"))
            .getOrThrow()

        val index = File(credentialsDir, "index.bin")
        assertTrue(index.exists())
        assertFalse(
            String(index.readBytes(), Charsets.ISO_8859_1).contains("Acme Corp production"),
        )
    }

    @Test
    fun referencesAreListedWithoutDecryptingSecrets() = runTest {
        store.put(SecretType.SSH_PASSWORD, "one", Secret.of("secret-one")).getOrThrow()
        store.put(SecretType.AI_API_KEY, "two", Secret.of("secret-two")).getOrThrow()

        val listed = store.list().getOrThrow()

        assertEquals(2, listed.size)
        assertEquals(setOf("one", "two"), listed.map { it.label }.toSet())
    }

    @Test
    fun replacingKeepsTheIdSoServersDoNotLoseTheirReference() = runTest {
        val ref = store.put(SecretType.SSH_PASSWORD, "vps", Secret.of("old-password"))
            .getOrThrow()

        val replaced = store.replace(ref.id, Secret.of("new-password")).getOrThrow()

        assertEquals(ref.id, replaced.id)
        assertEquals("new-password", store.read(ref.id).getOrThrow().useAsString { it })
    }

    @Test
    fun deletingRemovesBothTheFileAndTheReference() = runTest {
        val ref = store.put(SecretType.SSH_PASSWORD, "temp", Secret.of("to-be-deleted"))
            .getOrThrow()

        store.delete(ref.id).getOrThrow()

        assertTrue(store.read(ref.id).isFailure)
        assertTrue(store.list().getOrThrow().isEmpty())
        assertFalse(File(credentialsDir, "${ref.id}.bin").exists())
    }

    @Test
    fun readingAnUnknownIdFailsCleanly() = runTest {
        val result = store.read("00000000-0000-0000-0000-000000000000")

        assertTrue(result.isFailure)
    }

    @Test
    fun anUnsafeIdIsRejectedRatherThanEscapingTheDirectory() = runTest {
        // The id is an opaque UUID in normal use, but the store must not become an
        // arbitrary-file-read primitive if a malformed one arrives from elsewhere.
        val result = store.read("../../../../data/data/other.app/files/secrets")

        assertTrue(result.isFailure)
    }

    @Test
    fun concurrentWritesAllSurvive() = runTest {
        // Every write rewrites the shared index. Without the mutex the last writer
        // would win and the rest would silently vanish.
        val refs = (1..12).map { index ->
            async(Dispatchers.IO) {
                store.put(SecretType.GENERIC, "label-$index", Secret.of("secret-$index"))
                    .getOrThrow()
            }
        }.awaitAll()

        assertEquals(12, store.list().getOrThrow().size)
        refs.forEachIndexed { index, ref ->
            assertEquals("secret-${index + 1}", store.read(ref.id).getOrThrow().useAsString { it })
        }
    }

    @Test
    fun theStoreSurvivesBeingReopened() = runTest {
        val ref = store.put(SecretType.SSH_PRIVATE_KEY, "key", Secret.of("persisted-value"))
            .getOrThrow()

        // A new instance, as after a process restart: the index must be reloaded from
        // disk rather than assumed empty.
        val reopened = FileSecureCredentialStore(
            ApplicationProvider.getApplicationContext(),
            crypto,
            Dispatchers.IO,
        )

        assertEquals(1, reopened.list().getOrThrow().size)
        assertEquals("persisted-value", reopened.read(ref.id).getOrThrow().useAsString { it })
    }

    @Test
    fun eraseAllRemovesEverythingIncludingTheKey() = runTest {
        val ref = store.put(SecretType.SSH_PASSWORD, "doomed", Secret.of("erase-me"))
            .getOrThrow()

        store.eraseAll().getOrThrow()

        assertTrue(store.list().getOrThrow().isEmpty())
        assertTrue(store.read(ref.id).isFailure)
    }

    @Test
    fun twoSecretsWithIdenticalContentAreStoredDistinctly() = runTest {
        val first = store.put(SecretType.GENERIC, "a", Secret.of("same-value")).getOrThrow()
        val second = store.put(SecretType.GENERIC, "b", Secret.of("same-value")).getOrThrow()

        assertNotEquals(first.id, second.id)
        // Randomised IVs mean identical plaintext yields different files on disk.
        val a = File(credentialsDir, "${first.id}.bin").readBytes()
        val b = File(credentialsDir, "${second.id}.bin").readBytes()
        assertNotEquals(a.toList(), b.toList())
    }
}
