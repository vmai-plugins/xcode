plugins {
    id("vmstudio.android.feature")
}

android {
    namespace = "digital.vmstudio.code.feature.files"
}

dependencies {
    implementation(projects.core.sftp)
    implementation(projects.core.ssh)
    implementation(libs.androidx.activity.compose)
}
