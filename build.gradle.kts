import com.vanniktech.maven.publish.SonatypeHost
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
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
    // Web
    js {
        browser()
        nodejs()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    // Apple platforms
    macosX64()
    macosArm64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()
    watchosX64()
    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()
    watchosDeviceArm64()

    // JVM
    jvm()

    // Linux
    linuxX64()
    linuxArm64()

    // Windows
    mingwX64()

    // Android Native
    androidNativeX64()
    androidNativeX86()
    androidNativeArm32()
    androidNativeArm64()

    sourceSets {
        val commonMain by getting {
            explicitApi()
            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            compilerOptions {
                allWarningsAsErrors = true
                apiVersion.set(KotlinVersion.KOTLIN_2_0)
                languageVersion.set(KotlinVersion.KOTLIN_2_0)
            }
            dependencies {
                implementation("org.jetbrains:annotations:26.0.2")
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
