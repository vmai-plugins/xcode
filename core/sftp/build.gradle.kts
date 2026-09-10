plugins {
    id("vmstudio.android.library")
    id("vmstudio.android.hilt")
}

android {
    namespace = "digital.vmstudio.code.core.sftp"
}

dependencies {
    api(projects.core.common)
    api(projects.core.ssh)
    implementation(projects.core.database)
    implementation(libs.androidx.core.ktx)
}
