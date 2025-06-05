import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val xmlunitVersion = "2.10.0"

val ktlint: Configuration by configurations.creating

plugins {
    kotlin("jvm")
    id("io.gitlab.arturbosch.detekt")
    `maven-publish`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.hamcrest:hamcrest-core:2.2")
    testImplementation("org.xmlunit:xmlunit-matchers:$xmlunitVersion")
    testImplementation("org.xmlunit:xmlunit-jakarta-jaxb-impl:$xmlunitVersion")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    ktlint("com.pinterest.ktlint:ktlint-cli:1.5.0") {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling::class, Bundling.EXTERNAL))
        }
    }
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events(
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
            TestLogEvent.FAILED,
            TestLogEvent.STANDARD_ERROR,
            TestLogEvent.STANDARD_OUT,
        )
        exceptionFormat = TestExceptionFormat.FULL
    }

    // Always run task even if it has successfully completed earlier
    outputs.upToDateWhen { false }
}

tasks.register<Test>("updateTestExpectations") {
    useJUnitPlatform {
        includeTags("expectation-update")
        ignoreFailures = true
    }

    environment("eu.efti.updateTestExpectations", "true")

    // Always run task even if it has successfully completed earlier
    outputs.upToDateWhen { false }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(rootProject.file("detekt.yml"))
}

tasks.register<JavaExec>("ktlintCheck") {
    group = "verification"
    description = "Check Kotlin code style"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args = listOfNotNull(
        properties["ci"]?.let {
            "--reporter=plain?group_by_file"
        },
        "src/**/*.kt",
        "**.kts",
        "!**/build/**",
    )
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "Fix Kotlin code style deviations"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    jvmArgs = listOf("--add-opens=java.base/java.lang=ALL-UNNAMED")
    args = listOf("-F", "src/**/*.kt", "**.kts", "!**/build/**")
}

tasks.named("check") {
    dependsOn(
        tasks.getByName("detekt"),
        tasks.getByName("ktlintCheck"),
        tasks.getByName("test"),
    )
}


java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    repositories {
        maven {
            name = "LocalMavenRepo"
            url = uri(rootProject.layout.buildDirectory.dir("local-maven-repo"))
        }
    }
}
