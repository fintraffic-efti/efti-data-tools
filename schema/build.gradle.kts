plugins {
    id("data-tools.kotlin-conventions")
}

sourceSets {
    main {
        resources {
            srcDir(rootProject.file("xsd"))
        }
    }
}

dependencies {
    implementation("org.apache.xmlbeans:xmlbeans:5.3.0")

    runtimeOnly(platform("org.apache.logging.log4j:log4j-bom:2.24.1"))
    runtimeOnly("org.apache.logging.log4j:log4j-core")
}

java {
    withJavadocJar()
    withSourcesJar()
}

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
