plugins {
    id("vmstudio.android.feature")
}

android {
    namespace = "digital.vmstudio.code.feature.tasks"
}

dependencies {
    implementation(projects.core.ai)
}

