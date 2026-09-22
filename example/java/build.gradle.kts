import java.net.URI

plugins {
    `java-library`
}

val libraryVersion: String by lazy {
    rootProject.file("../../gradle.properties")
        .readLines()
        .first { it.startsWith("version=") }
        .split("=")
        .last()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    maven {
        url = URI("file://$rootDir/../../build/local-maven-repo")
    }
    mavenCentral()
}

dependencies {
    implementation("eu.efti.datatools:schema:$libraryVersion")

    testImplementation(platform("org.junit:junit-bom:5.11.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    testImplementation("eu.efti.datatools:populate:$libraryVersion")
}

val eftiXsdResources = layout.buildDirectory.dir("efti-xsd-resources")

val copyEftiXsd by tasks.registering(Copy::class) {
    from(file("../../xsd/v0"))
    into(eftiXsdResources.map { it.dir("efti-xsd") })
}

sourceSets {
    main {
        output.dir(mapOf("builtBy" to copyEftiXsd), eftiXsdResources)
    }
}

tasks.test {
    useJUnitPlatform()
}
