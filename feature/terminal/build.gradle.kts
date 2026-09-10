plugins {
    id("vmstudio.android.feature")
}

android {
    namespace = "digital.vmstudio.code.feature.terminal"
}

dependencies {
    implementation(projects.core.terminal)
    implementation(projects.core.ssh)
}
