plugins {
    id("vmstudio.android.feature")
}

android {
    namespace = "digital.vmstudio.code.feature.connectors"
}

dependencies {
    implementation(projects.core.connectors)
    implementation(projects.core.database)
    implementation(projects.core.ssh)
}
