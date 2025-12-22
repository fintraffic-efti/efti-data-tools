plugins {
    `kotlin-dsl`
}

val kotlinVersion = "2.3.0"
val detektVersion = "1.23.8"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:$kotlinVersion")
    implementation("io.gitlab.arturbosch.detekt:io.gitlab.arturbosch.detekt.gradle.plugin:${detektVersion}")
}
