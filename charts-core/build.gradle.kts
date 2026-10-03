import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    testImplementation(libs.junit)
}

val benchmark by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations[benchmark.implementationConfigurationName].extendsFrom(configurations.implementation.get())

tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Reports warmed-up core sampling and lookup timing on 100,000 points."
    classpath = benchmark.runtimeClasspath
    mainClass.set("com.joelromanpr.charts.core.CoreBenchmark")
}

mavenPublishing {
    configure(KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml")))
    publishToMavenCentral()
    signAllPublications()
    pom {
        name.set("Jetpack Charts Core")
        description.set("Immutable chart data, geometry, and financial indicators for Kotlin.")
    }
}
