plugins {
    id("vmstudio.android.feature")
}

android {
    namespace = "digital.vmstudio.code.feature.projects"
}

dependencies {
    api(projects.core.project)
    implementation(projects.core.ssh)
}
