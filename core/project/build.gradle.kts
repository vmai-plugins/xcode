plugins {
    id("vmstudio.android.library")
    id("vmstudio.android.hilt")
}

android {
    namespace = "digital.vmstudio.code.core.project"
}

dependencies {
    api(projects.core.common)
    api(projects.core.database)
}
