plugins {
    id("vmstudio.android.library")
    id("vmstudio.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "digital.vmstudio.code.core.update"
}

dependencies {
    api(projects.core.common)
    implementation(projects.core.network)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
}
