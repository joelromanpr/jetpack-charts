import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.binary.compatibility)
}

allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
    tasks.withType<AbstractArchiveTask>().configureEach {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

apiValidation {
    ignoredProjects.add("sample")
}

subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<MavenPublishBaseExtension> {
            pom {
                developers {
                    developer {
                        id.set("joelromanpr")
                        name.set("Joel Roman")
                        email.set("contact@joelromanpr.com")
                        url.set("https://github.com/joelromanpr")
                        organization.set("Joel Roman")
                        organizationUrl.set("https://github.com/joelromanpr")
                    }
                }
            }
        }
    }
}

tasks.register<Zip>("stageRelease") {
    group = "publishing"
    description = "Packages unsigned binaries, sources, API documentation, and publication metadata for review."
    dependsOn(
        ":charts-core:jar", ":charts-core:sourcesJar", ":charts-core:dokkaJavadocJar",
        ":charts-core:generatePomFileForMavenPublication", ":charts-core:generateMetadataFileForMavenPublication",
        ":charts-compose:bundleReleaseAar", ":charts-compose:sourceReleaseJar", ":charts-compose:dokkaJavadocJar",
        ":charts-compose:generatePomFileForMavenPublication", ":charts-compose:generateMetadataFileForMavenPublication",
    )
    archiveFileName.set("jetpack-charts-${project.version}-unsigned.zip")
    destinationDirectory.set(layout.projectDirectory.dir("outputs/releases"))
    listOf("charts-core", "charts-compose").forEach { module ->
        val coordinatePath = "${project.group.toString().replace('.', '/')}/$module/${project.version}"
        from(project(module).layout.buildDirectory.dir("libs")) {
            into(coordinatePath)
            if (module == "charts-core") include("$module-${project.version}*.jar") else include("$module-${project.version}-javadoc.jar")
        }
        from(project(module).layout.buildDirectory.dir("publications/maven")) {
            into(coordinatePath)
            include("pom-default.xml", "module.json")
            rename("pom-default.xml", "$module-${project.version}.pom")
            rename("module.json", "$module-${project.version}.module")
        }
    }
    from(project(":charts-compose").layout.buildDirectory.dir("outputs/aar")) {
        into("${project.group.toString().replace('.', '/')}/charts-compose/${project.version}")
        include("*-release.aar")
        rename("charts-compose-release.aar", "charts-compose-${project.version}.aar")
    }
    from(providers.provider { project(":charts-compose").tasks.named("sourceReleaseJar").get().outputs.files }) {
        into("${project.group.toString().replace('.', '/')}/charts-compose/${project.version}")
        include("release-sources.jar")
        rename("release-sources.jar", "charts-compose-${project.version}-sources.jar")
    }
}
