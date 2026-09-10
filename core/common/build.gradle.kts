plugins {
    id("vmstudio.android.library")
    id("vmstudio.android.hilt")
}

android {
    namespace = "digital.vmstudio.code.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.ktx)
}
