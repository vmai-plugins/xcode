plugins {
    id("vmstudio.android.library")
    id("vmstudio.android.hilt")
    id("vmstudio.android.room")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "digital.vmstudio.code.core.database"
}

dependencies {
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    api(projects.core.common)
    implementation(projects.core.security)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
}
