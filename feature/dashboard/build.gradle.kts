plugins {
    id("vmstudio.android.feature")
}

android {
    namespace = "digital.vmstudio.code.feature.dashboard"
}

dependencies {
    implementation(projects.core.ssh)
    implementation(projects.core.database)
}
