package digital.vmstudio.code.core.security.store

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.preferences.UserPreferencesRepository
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.security.model.Secret
import digital.vmstudio.code.core.security.model.SecretType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HubCredentialManager @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val credentialStore: SecureCredentialStore,
) {
    private val mutex = Mutex()

    suspend fun read(): VmResult<Secret?> = mutex.withLock {
        val preferences = preferencesRepository.preferences.first()
        preferences.hubApiKeyCredentialId?.let {
            if (preferences.legacyHubApiKey != null) {
                preferencesRepository.clearLegacyHubApiKey()
            }
            return credentialStore.read(it).mapSecret()
        }

        val legacyKey = preferences.legacyHubApiKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: return VmResult.Success(null)
        val legacySecret = Secret.of(legacyKey)
        try {
            return when (
                val stored = credentialStore.put(
                    type = SecretType.HUB_API_KEY,
                    label = "VM Project Hub API key",
                    secret = legacySecret,
                )
            ) {
                is VmResult.Success -> {
                    preferencesRepository.setHubApiKeyCredentialId(stored.value.id)
                    preferencesRepository.clearLegacyHubApiKey()
                    credentialStore.read(stored.value.id).mapSecret()
                }
                is VmResult.Failure -> stored
            }
        } finally {
            legacySecret.wipe()
        }
    }

    suspend fun store(key: String): VmResult<Unit> = mutex.withLock {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            return@withLock VmResult.Failure(VmError.Validation("Hub API key cannot be empty"))
        }

        val previousId = preferencesRepository.preferences.first().hubApiKeyCredentialId
        val secret = Secret.of(trimmed)
        try {
            when (
                val stored = credentialStore.put(
                    type = SecretType.HUB_API_KEY,
                    label = "VM Project Hub API key",
                    secret = secret,
                )
            ) {
                is VmResult.Success -> {
                    preferencesRepository.setHubApiKeyCredentialId(stored.value.id)
                    preferencesRepository.clearLegacyHubApiKey()
                    if (previousId != null && previousId != stored.value.id) {
                        when (val deleted = credentialStore.delete(previousId)) {
                            is VmResult.Success -> VmResult.Success(Unit)
                            is VmResult.Failure -> VmResult.Failure(
                                VmError.Unexpected(
                                    summary = "Hub API key was saved securely, but the previous credential could not be removed",
                                    reason = deleted.error.summary,
                                )
                            )
                        }
                    } else {
                        VmResult.Success(Unit)
                    }
                }
                is VmResult.Failure -> stored
            }
        } finally {
            secret.wipe()
        }
    }

    suspend fun clear(): VmResult<Unit> = mutex.withLock {
        val credentialId = preferencesRepository.preferences.first().hubApiKeyCredentialId
        if (credentialId != null) {
            when (val deleted = credentialStore.delete(credentialId)) {
                is VmResult.Failure -> return@withLock deleted
                is VmResult.Success -> Unit
            }
        }
        preferencesRepository.setHubApiKeyCredentialId(null)
        preferencesRepository.clearLegacyHubApiKey()
        VmResult.Success(Unit)
    }

    private fun VmResult<Secret>.mapSecret(): VmResult<Secret?> = when (this) {
        is VmResult.Success -> VmResult.Success(value)
        is VmResult.Failure -> this
    }
}
