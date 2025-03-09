plugins {
    kotlin("plugin.serialization")
    `java-test-fixtures`
}

dependencies {
    api(project(":opentaint-ir-core"))
    api(project(":opentaint-ir-api"))

    implementation(Libs.kotlin_logging)
    implementation(Libs.slf4j_simple)
    implementation(Libs.kotlinx_coroutines_core)
    implementation(Libs.kotlinx_serialization_json)

    testImplementation(testFixtures(project(":opentaint-ir-core")))
    testImplementation(Libs.javax_servlet_api)
    testImplementation(Libs.joda_time)
    testImplementation(files("src/test/resources/juliet.jar"))
    testImplementation(files("src/test/resources/pointerbench.jar"))
}
