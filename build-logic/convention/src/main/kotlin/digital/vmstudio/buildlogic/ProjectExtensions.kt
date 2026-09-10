package digital.vmstudio.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/**
 * Shared Android + Kotlin configuration applied to every Android module so that
 * SDK levels, Java/Kotlin targets and compiler strictness cannot drift per module.
 */
internal fun Project.configureAndroidKotlin(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
) {
    commonExtension.apply {
        compileSdk = BuildConfig.COMPILE_SDK

        defaultConfig {
            minSdk = BuildConfig.MIN_SDK
            testInstrumentationRunner = BuildConfig.TEST_RUNNER
        }

        compileOptions {
            sourceCompatibility = BuildConfig.JAVA_VERSION
            targetCompatibility = BuildConfig.JAVA_VERSION
        }

        packaging {
            resources {
                // sshj / BouncyCastle / JGit each ship overlapping metadata.
                excludes += setOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "/META-INF/DEPENDENCIES",
                    "/META-INF/LICENSE*",
                    "/META-INF/NOTICE*",
                    "/META-INF/INDEX.LIST",
                    "/META-INF/*.kotlin_module",
                    "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                )
            }
        }

        testOptions {
            unitTests {
                isIncludeAndroidResources = true
                isReturnDefaultValues = true
            }
        }
    }

    extensions.configure(KotlinAndroidProjectExtension::class.java) {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(commonKotlinCompilerArgs())
        }
    }
}

internal fun Project.configureKotlinJvm() {
    extensions.configure(JavaPluginExtension::class.java) {
        sourceCompatibility = BuildConfig.JAVA_VERSION
        targetCompatibility = BuildConfig.JAVA_VERSION
    }
    extensions.configure(KotlinJvmProjectExtension::class.java) {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(commonKotlinCompilerArgs())
        }
    }
}

private fun commonKotlinCompilerArgs(): List<String> = listOf(
    "-opt-in=kotlin.RequiresOptIn",
    "-Xconsistent-data-class-copy-visibility",
    // Adopt the Kotlin 2.3 behaviour now: annotations on constructor parameters
    // (e.g. @ApplicationContext, @IoDispatcher) apply to the parameter *and* the
    // generated field, which is what Hilt qualifiers need.
    "-Xannotation-default-target=param-property",
)
