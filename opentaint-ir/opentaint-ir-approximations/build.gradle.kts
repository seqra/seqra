dependencies {
    implementation(project(":opentaint-ir-api-jvm"))
    implementation(project(":opentaint-ir-core"))
    implementation(testFixtures(project(":opentaint-ir-core")))

    testImplementation(Libs.kotlin_logging)
    testRuntimeOnly(Libs.guava)
}
