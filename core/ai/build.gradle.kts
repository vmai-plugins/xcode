plugins {
    id("vmstudio.android.library")
    id("vmstudio.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "digital.vmstudio.code.core.ai"
}

dependencies {
    api(projects.core.common)
    api(projects.core.ssh)
    api(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.sftp)
    implementation(projects.core.terminal)
    implementation(libs.androidx.core.ktx)
    api(libs.kotlinx.serialization.json)
}
