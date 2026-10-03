import com.android.build.api.artifact.SingleArtifact
import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
}

android {
    namespace = "com.joelromanpr.charts.compose"
    compileSdk = 37
    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    testOptions { targetSdk = 36 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

kotlin {
    jvmToolchain(17)
    explicitApi()
    compilerOptions { moduleName.set("charts-compose_release") }
}

val apiValidatorRuntime = configurations.create("apiValidatorRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    api(project(":charts-core"))
    api(platform(libs.compose.bom))
    api(libs.compose.runtime)
    api(libs.compose.ui)
    api(libs.compose.foundation)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.compose.ui.test.manifest)
    apiValidatorRuntime(libs.asm)
    apiValidatorRuntime(libs.asm.tree)
    apiValidatorRuntime(libs.kotlin.metadata.jvm)
}

// BCV's automatic Android hook requires the retired Kotlin Android plugin.
// Check the published classes through AGP's public artifact API instead.
androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    val classes = tasks.register<Sync>("extractReleaseApiClasses") {
        from(zipTree(variant.artifacts.get(SingleArtifact.AAR))) { include("classes.jar") }
        into(layout.buildDirectory.dir("api/classes"))
    }
    val apiBuild = tasks.register<KotlinApiBuildTask>("apiBuild") {
        dependsOn(classes)
        inputJar.set(layout.buildDirectory.file("api/classes/classes.jar"))
        outputApiFile.set(layout.buildDirectory.file("api/charts-compose.api"))
        runtimeClasspath.from(apiValidatorRuntime)
    }
    val apiCheck = tasks.register<KotlinApiCompareTask>("apiCheck") {
        group = "verification"
        description = "Checks the published Android API against the committed baseline."
        projectApiFile.set(layout.projectDirectory.file("api/charts-compose.api"))
        generatedApiFile.set(apiBuild.flatMap { it.outputApiFile })
    }
    val apiDump = tasks.register<Copy>("apiDump") {
        group = "verification"
        description = "Updates the Android API baseline for an intentional API change."
        from(apiBuild.flatMap { it.outputApiFile })
        into(layout.projectDirectory.dir("api"))
    }
    apiCheck.configure { mustRunAfter(apiDump) }
    tasks.named("check") { dependsOn(apiCheck) }
}

mavenPublishing {
    configure(AndroidSingleVariantLibrary(variant = "release", javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml")))
    publishToMavenCentral()
    signAllPublications()
    pom {
        name.set("Jetpack Charts Compose")
        description.set("Interactive, themeable charts for Android Jetpack Compose.")
    }
}
