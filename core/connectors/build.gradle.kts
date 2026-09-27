plugins {
    id("vmstudio.android.library")
    id("vmstudio.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "digital.vmstudio.code.core.connectors"
}

dependencies {
    api(projects.core.common)
    api(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.security)
    implementation(projects.core.ssh)
    implementation(projects.core.ai)
    implementation(libs.androidx.core.ktx)
    api(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
}
