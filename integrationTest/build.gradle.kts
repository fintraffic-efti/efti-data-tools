plugins {
    id("data-tools.kotlin-conventions")
}

dependencies {
    testImplementation(project(":populate"))
    testImplementation(project(":schema"))
}
