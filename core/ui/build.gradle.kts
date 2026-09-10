plugins {
    id("vmstudio.android.library")
    id("vmstudio.android.compose")
}

android {
    namespace = "digital.vmstudio.code.core.ui"
}

dependencies {
    api(projects.core.common)
    api(libs.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.material3.window.size)
}
