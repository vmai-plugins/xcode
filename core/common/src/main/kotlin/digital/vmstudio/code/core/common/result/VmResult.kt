package digital.vmstudio.code.core.common.result

import digital.vmstudio.code.core.common.error.VmError
import kotlin.coroutines.cancellation.CancellationException

/**
 * The single return type for every fallible operation in the app.
 *
 * Using an explicit result rather than exceptions keeps failure handling visible
 * at each call site, which is what makes the "never show Unknown error" rule
 * enforceable: a [VmError] must be constructed deliberately at the boundary where
 * the real cause is still known.
 */
sealed interface VmResult<out T> {

    data class Success<out T>(val value: T) : VmResult<T>

    data class Failure(val error: VmError) : VmResult<Nothing>

    val isSuccess: Boolean get() = this is Success

    val isFailure: Boolean get() = this is Failure
}

fun <T> T.asSuccess(): VmResult<T> = VmResult.Success(this)

fun VmError.asFailure(): VmResult<Nothing> = VmResult.Failure(this)

fun <T> VmResult<T>.getOrNull(): T? = (this as? VmResult.Success)?.value

fun <T> VmResult<T>.errorOrNull(): VmError? = (this as? VmResult.Failure)?.error

fun <T> VmResult<T>.getOrDefault(default: T): T = getOrNull() ?: default

fun <T> VmResult<T>.getOrThrow(): T = when (this) {
    is VmResult.Success -> value
    is VmResult.Failure -> throw VmErrorException(error)
}

inline fun <T, R> VmResult<T>.map(transform: (T) -> R): VmResult<R> = when (this) {
    is VmResult.Success -> VmResult.Success(transform(value))
    is VmResult.Failure -> this
}

inline fun <T, R> VmResult<T>.flatMap(transform: (T) -> VmResult<R>): VmResult<R> = when (this) {
    is VmResult.Success -> transform(value)
    is VmResult.Failure -> this
}

inline fun <T> VmResult<T>.mapError(transform: (VmError) -> VmError): VmResult<T> = when (this) {
    is VmResult.Success -> this
    is VmResult.Failure -> VmResult.Failure(transform(error))
}

inline fun <T> VmResult<T>.onSuccess(action: (T) -> Unit): VmResult<T> = apply {
    if (this is VmResult.Success) action(value)
}

inline fun <T> VmResult<T>.onFailure(action: (VmError) -> Unit): VmResult<T> = apply {
    if (this is VmResult.Failure) action(error)
}

inline fun <T, R> VmResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (VmError) -> R,
): R = when (this) {
    is VmResult.Success -> onSuccess(value)
    is VmResult.Failure -> onFailure(error)
}

/**
 * Runs [block], converting any throwable into a [VmError] via [mapper].
 *
 * [CancellationException] is deliberately rethrown: swallowing it would break
 * structured concurrency and leave coroutines running after their scope is gone.
 */
inline fun <T> vmCatching(
    mapper: (Throwable) -> VmError = { VmError.Unexpected(cause = it, reason = it.message) },
    block: () -> T,
): VmResult<T> = try {
    VmResult.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    VmResult.Failure(mapper(throwable))
}

/** Thrown only by [getOrThrow]; carries the structured error for boundaries that need it. */
class VmErrorException(val error: VmError) : Exception(error.summary, error.cause)
