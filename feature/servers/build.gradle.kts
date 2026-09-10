plugins {
    id("vmstudio.android.feature")
}

android {
    namespace = "digital.vmstudio.code.feature.servers"
}

dependencies {
    implementation(projects.core.ssh)
    implementation(projects.core.database)
}
