plugins {
    id("vmstudio.android.feature")
}

android {
    namespace = "digital.vmstudio.code.feature.ai"
}

dependencies {
    implementation(projects.core.ai)
    implementation(projects.core.ssh)
    implementation(projects.core.sftp)
    implementation(projects.core.git)
}
