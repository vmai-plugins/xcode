import com.android.build.api.dsl.CommonExtension
import digital.vmstudio.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Enables Compose for a module that already has the Android application or library
 * plugin applied. Kotlin 2.x ships the Compose compiler as a Kotlin plugin, so no
 * separate compiler-extension version needs pinning.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        val commonExtension = extensions.findByType(CommonExtension::class.java)
            ?: error("vmstudio.android.compose requires the Android application or library plugin")

        commonExtension.buildFeatures.compose = true

        val bom = libs.findLibrary("compose-bom").get()
        dependencies {
            add("implementation", platform(bom))
            add("androidTestImplementation", platform(bom))
            add("implementation", libs.findLibrary("compose-ui").get())
            add("implementation", libs.findLibrary("compose-ui-graphics").get())
            add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
            add("implementation", libs.findLibrary("compose-material3").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
            add("debugImplementation", libs.findLibrary("compose-ui-test-manifest").get())
            add("androidTestImplementation", libs.findLibrary("compose-ui-test-junit4").get())
        }
    }
}
