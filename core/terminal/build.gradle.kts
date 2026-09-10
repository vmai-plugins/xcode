plugins {
    id("vmstudio.android.library")
    id("vmstudio.android.hilt")
}

android {
    namespace = "digital.vmstudio.code.core.terminal"
}

dependencies {
    api(projects.core.common)
    api(projects.core.ssh)
    implementation(libs.androidx.core.ktx)
}
