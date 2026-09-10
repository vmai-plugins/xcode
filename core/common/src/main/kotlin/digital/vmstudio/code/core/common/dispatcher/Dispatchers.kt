package digital.vmstudio.code.core.common.dispatcher

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class MainDispatcher

/**
 * Long-lived scope for work that must outlive any single screen: an in-flight SFTP
 * transfer, an agent run, a terminal session. Injected rather than created ad hoc so
 * tests can substitute a controllable scope.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
