plugins {
    id("vmstudio.android.feature")
}

android {
    namespace = "digital.vmstudio.code.feature.settings"
}

dependencies {
    implementation(projects.core.security)
    implementation(projects.core.ai)
    implementation(projects.core.update)
}
