plugins {
    id("vmstudio.android.feature")
    id("vmstudio.android.compose")
}

android {
    namespace = "digital.vmstudio.code.feature.browser"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.ui)
    implementation(projects.core.ai)
    implementation(libs.androidx.activity.compose)
}
