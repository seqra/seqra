dependencies {
    implementation(project(":opentaint-ir-api-jvm"))
    implementation(project(":opentaint-ir-core"))

    testImplementation(testFixtures(project(":opentaint-ir-core")))
    testImplementation(testFixtures(project(":opentaint-ir-storage")))
    testImplementation(Libs.kotlin_logging)
    testRuntimeOnly(Libs.guava)
}
