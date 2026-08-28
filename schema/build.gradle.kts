plugins {
    id("data-tools.kotlin-conventions")
    `java-test-fixtures`
}

// The xsd schema files are deliberately not packaged into the published artifacts: users of this library provide
// their own copy of the schemas. Tests get them from the repository, see below and the kotlin conventions plugin.
val testClasspathXsdPrefix = "efti-xsd"
val testXsdPrefixRoot: Provider<Directory> = layout.buildDirectory.dir("test-xsd/prefixed")
val testXsdClasspathRoot: Provider<Directory> = layout.buildDirectory.dir("test-xsd/root")

val copyTestXsdToPrefixedClasspath by tasks.registering(Copy::class) {
    from(rootProject.file("xsd"))
    into(testXsdPrefixRoot.map { it.dir(testClasspathXsdPrefix) })
}

val copyTestXsdToClasspathRoot by tasks.registering(Copy::class) {
    from(rootProject.file("xsd"))
    into(testXsdClasspathRoot)
}

sourceSets {
    test {
        runtimeClasspath += files(testXsdPrefixRoot, testXsdClasspathRoot)
    }
}

tasks.test {
    dependsOn(copyTestXsdToPrefixedClasspath, copyTestXsdToClasspathRoot)
    systemProperty("eu.efti.datatools.test.xsdClasspathPrefix", testClasspathXsdPrefix)
}

dependencies {
    implementation("org.apache.xmlbeans:xmlbeans:5.3.0")

    runtimeOnly(platform("org.apache.logging.log4j:log4j-bom:2.25.4"))
    runtimeOnly("org.apache.logging.log4j:log4j-core")
}

java {
    withJavadocJar()
    withSourcesJar()
}

// Test fixtures are meant for the tests of this repository only, do not publish them.
val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
javaComponent.withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = rootProject.group as String
            artifactId = "schema"
            version = rootProject.version as String

            from(components["java"])

            pom {
                name = "schema"
                description = "efti-data-tools schema utilities"
                url = "https://github.com/fintraffic-efti/efti-data-tools"

                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }

                scm {
                    connection = "scm:git:git@github.com:fintraffic-efti/efti-data-tools.git"
                    developerConnection = "scm:git:git@github.com:fintraffic-efti/efti-data-tools.git"
                    url = "https://github.com/fintraffic-efti/efti-data-tools"
                }
            }
        }
    }
}
