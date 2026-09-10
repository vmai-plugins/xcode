plugins {
    id("vmstudio.android.library")
    id("vmstudio.android.hilt")
}

android {
    namespace = "digital.vmstudio.code.core.ssh"
}

dependencies {
    api(projects.core.common)
    api(projects.core.security)
    implementation(projects.core.database)
    implementation(libs.androidx.core.ktx)

    // sshj pulls slf4j; Android has no binding, so a no-op is supplied at runtime
    // by excluding nothing and letting slf4j fall back to its NOP logger. See SSH.md.
    api(libs.sshj)
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.eddsa)

    // Parse `pm2 jlist` in the app scanner. Same Json model the AI provider layer
    // uses for stream envelopes.
    implementation(libs.kotlinx.serialization.json)
}
