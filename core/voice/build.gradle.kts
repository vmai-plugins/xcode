plugins {
    id("vmstudio.android.library")
    id("vmstudio.android.hilt")
}

android {
    namespace = "digital.vmstudio.code.core.voice"
}

dependencies {
    api(projects.core.common)
    implementation(libs.androidx.core.ktx)
}
