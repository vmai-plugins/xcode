plugins {
    id("vmstudio.android.feature")
}

android {
    namespace = "digital.vmstudio.code.feature.editor"
}

dependencies {
    implementation(projects.core.editor)
    implementation(projects.core.ssh)
    implementation(projects.core.sftp)
}
