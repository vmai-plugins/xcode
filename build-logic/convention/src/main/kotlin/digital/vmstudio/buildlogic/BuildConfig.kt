package digital.vmstudio.buildlogic

import org.gradle.api.JavaVersion

/**
 * Single source of truth for SDK / toolchain levels across all modules.
 *
 * minSdk is 26 deliberately: JGit and the NIO-backed local workspace layer rely on
 * `java.nio.file` and `java.time`, which are only available without desugaring
 * limitations from Android 8.0 onwards. Dropping below 26 would mean shipping a
 * degraded Git implementation, which the product spec rules out.
 */
object BuildConfig {
    const val COMPILE_SDK = 36
    const val TARGET_SDK = 36
    const val MIN_SDK = 26

    const val APPLICATION_ID = "digital.vmstudio.code"
    const val VERSION_CODE = 4
    const val VERSION_NAME = "0.4.0"

    val JAVA_VERSION = JavaVersion.VERSION_17
    const val JVM_TARGET = "17"

    const val TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner"
}
