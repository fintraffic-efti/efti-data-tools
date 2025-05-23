plugins {
    id("data-tools.kotlin-conventions")
}

dependencies {
    api(project(":schema"))
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = rootProject.group as String
            artifactId = "populate"
            version = rootProject.version as String

            from(components["java"])

            pom {
                name = "populate"
                description = "efti-data-tools populate utilities"
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
