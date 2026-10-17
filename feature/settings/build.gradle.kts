plugins {
    id("vmstudio.android.feature")
}

android {
    namespace = "digital.vmstudio.code.feature.settings"
}

dependencies {
    implementation(projects.core.git)
    implementation(projects.core.security)
    implementation(projects.core.ai)
    implementation(projects.core.ssh)
    implementation(projects.core.database)
    implementation(projects.core.update)
}
