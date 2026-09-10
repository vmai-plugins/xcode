plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt)
}

/**
 * Detekt runs from the root over every module's sources rather than being applied
 * per module. A single invocation means one report and one baseline, and it keeps
 * the convention plugins free of analysis concerns.
 */
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/detekt.yml"))
    baseline = file("$rootDir/detekt-baseline.xml")
    parallel = true
    source.setFrom(
        files(
            subprojects.map { "${it.projectDir}/src/main/kotlin" },
            subprojects.map { "${it.projectDir}/src/test/kotlin" },
        ),
    )
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(false)
        md.required.set(false)
    }
    // Generated sources are not written by hand and are not worth reporting on.
    exclude("**/build/**", "**/generated/**")
}

tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    jvmTarget = "17"
    exclude("**/build/**", "**/generated/**")
}
