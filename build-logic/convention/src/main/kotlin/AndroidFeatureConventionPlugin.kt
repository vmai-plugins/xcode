import digital.vmstudio.buildlogic.libs
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Baseline for every `:feature:*` module. Features may depend on core modules but
 * never on one another — cross-feature navigation goes through the app module's
 * navigation graph, which keeps the feature layer a flat, independently
 * buildable set.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("vmstudio.android.library")
        pluginManager.apply("vmstudio.android.compose")
        pluginManager.apply("vmstudio.android.hilt")

        dependencies {
            add("implementation", project(":core:common"))
            add("implementation", project(":core:ui"))
            add("implementation", libs.findLibrary("androidx-core-ktx").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-ktx").get())
            add("implementation", libs.findLibrary("androidx-navigation-compose").get())
            add("implementation", libs.findLibrary("hilt-navigation-compose").get())
            add("implementation", libs.findLibrary("compose-material-icons-extended").get())
        }

        val featurePath = path
        val verifyFeatureBoundaries = tasks.register("verifyFeatureBoundaries") {
            group = "verification"
            description = "Checks that this feature does not depend directly on another feature."
            doLast {
                val violations = configurations.flatMap { configuration ->
                    configuration.dependencies
                        .withType(ProjectDependency::class.java)
                        .filter {
                            it.dependencyProject.path.startsWith(":feature:") &&
                                it.dependencyProject.path != featurePath
                        }
                        .map { "${configuration.name} -> ${it.dependencyProject.path}" }
                }.distinct()
                check(violations.isEmpty()) {
                    "Feature modules cannot depend on other features: ${violations.joinToString()}"
                }
            }
        }
        tasks.named("check").configure {
            dependsOn(verifyFeatureBoundaries)
        }
    }
}
