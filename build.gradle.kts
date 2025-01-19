import com.vanniktech.maven.publish.SonatypeHost
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("multiplatform") version "2.0.21"
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "io.github.goquati"
version = System.getenv("GIT_TAG_VERSION") ?: "1.0-SNAPSHOT"
val githubUser = "goquati"
val githubProject = "qr"
val artifactId = githubProject
val descriptionStr = "A Kotlin Multiplatform library for generating QR codes effortlessly across platforms."

repositories {
    mavenCentral()
}

kover {
    reports {
        filters {
            excludes {
                classes("io.github.goquati.qr.Helper")
            }
        }
        verify {
            CoverageUnit.values().forEach { covUnit ->
                rule("minimal ${covUnit.name.lowercase()} coverage rate") {
                    minBound(100, coverageUnits = covUnit)
                }
            }
        }
    }
}

kotlin {
    jvm()
    js {
        browser()
        nodejs()
    }
    macosX64()
    macosArm64()
    linuxX64()
    linuxArm64()
    mingwX64()
    sourceSets {
        val commonMain by getting {
            explicitApi()
            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            compilerOptions {
                allWarningsAsErrors = true
                apiVersion.set(KotlinVersion.KOTLIN_2_0)
                languageVersion.set(KotlinVersion.KOTLIN_2_0)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test")
                implementation("io.kotest:kotest-assertions-core:5.9.0")
            }
        }
    }
}

mavenPublishing {
    val artifactId = "qr"
    coordinates(
        groupId = project.group as String,
        artifactId = artifactId,
        version = project.version as String
    )
    pom {
        name = artifactId
        description = descriptionStr
        url = "https://github.com/$githubUser/$githubProject"
        licenses {
            license {
                name = "MIT License"
                url = "https://github.com/$githubUser/$githubProject/blob/main/LICENSE"
            }
        }
        developers {
            developer {
                id = githubUser
                name = githubUser
                url = "https://github.com/$githubUser"
            }
        }
        scm {
            url = "https://github.com/${githubUser}/${githubProject}"
            connection = "scm:git:https://github.com/${githubUser}/${githubProject}.git"
            developerConnection = "scm:git:git@github.com:${githubUser}/${githubProject}.git"
        }
    }
    publishToMavenCentral(
        SonatypeHost.CENTRAL_PORTAL,
        automaticRelease = true,
    )
    signAllPublications()
}
