plugins {
    id("vmstudio.android.feature")
}

android {
    namespace = "digital.vmstudio.code.feature.projects"
}

dependencies {
    api(projects.core.project)
    implementation(projects.core.ssh)
    implementation(projects.core.ai)
    implementation(projects.core.git)
    implementation(projects.core.database)
    implementation(projects.core.update)
    implementation(projects.core.sftp)
}
