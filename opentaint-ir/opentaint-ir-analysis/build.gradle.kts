plugins {
    kotlin("plugin.serialization")
    `java-test-fixtures`
}

dependencies {
    api(project(":opentaint-ir-core"))
    api(project(":opentaint-ir-api"))
    api(project(":opentaint-ir-taint-configuration"))

    implementation(Libs.kotlin_logging)
    implementation(Libs.kotlin_logging5)
    implementation(Libs.slf4j_simple)
    implementation(Libs.kotlinx_coroutines_core)
    implementation(Libs.kotlinx_serialization_json)
    implementation(Libs.jdot)

    testImplementation(testFixtures(project(":opentaint-ir-core")))
    testImplementation(project(":opentaint-ir-api"))
    testImplementation(files("src/test/resources/pointerbench.jar"))
    testImplementation(Libs.joda_time)
    testImplementation(Libs.juliet_support)
    for (cweNum in listOf(89, 476, 563, 690)) {
        testImplementation(Libs.juliet_cwe(cweNum))
    }
}
