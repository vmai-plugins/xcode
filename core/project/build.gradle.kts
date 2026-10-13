plugins {
    id("vmstudio.android.library")
    id("vmstudio.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "digital.vmstudio.code.core.project"
}

dependencies {
    api(projects.core.common)
    api(projects.core.database)
    implementation(projects.core.network)
    implementation(projects.core.ssh)
    api(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
}
