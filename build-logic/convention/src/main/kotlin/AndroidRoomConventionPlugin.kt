import com.google.devtools.ksp.gradle.KspExtension
import digital.vmstudio.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")

        // Exported schemas are checked in so migrations can be tested against the
        // real historical schema rather than a regenerated approximation.
        val schemaDir = layout.projectDirectory.dir("schemas")
        extensions.configure<KspExtension> {
            arg("room.schemaLocation", schemaDir.asFile.absolutePath)
            arg("room.generateKotlin", "true")
        }

        dependencies {
            add("implementation", libs.findLibrary("room-runtime").get())
            add("implementation", libs.findLibrary("room-ktx").get())
            add("implementation", libs.findLibrary("room-paging").get())
            add("ksp", libs.findLibrary("room-compiler").get())
            add("testImplementation", libs.findLibrary("room-testing").get())
        }
    }
}
