plugins {
    id("vmstudio.android.library")
    id("vmstudio.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "digital.vmstudio.code.core.security"
}

dependencies {
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    api(projects.core.common)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
}
