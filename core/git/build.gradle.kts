plugins {
    id("vmstudio.android.library")
    id("vmstudio.android.hilt")
}

android {
    namespace = "digital.vmstudio.code.core.git"
}

dependencies {
    api(projects.core.common)
    implementation(projects.core.ssh)
}

