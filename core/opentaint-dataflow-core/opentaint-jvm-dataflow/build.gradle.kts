import OpentaintIrDependency.opentaint_ir_api_jvm
import OpentaintIrDependency.opentaint_ir_api_storage
import OpentaintIrDependency.opentaint_ir_core
import OpentaintIrDependency.opentaint_ir_storage
import OpentaintUtilDependency.opentaintUtilCommon
import OpentaintUtilDependency.opentaintUtilJvm
import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
}

dependencies {
    api(project(":opentaint-dataflow"))
    implementation(opentaintUtilCommon)
    implementation(opentaintUtilJvm)
    implementation("org.opentaint.opentaint-configuration-rules:configuration-rules-jvm")

    implementation(opentaint_ir_api_jvm)
    implementation(opentaint_ir_core)
    implementation(opentaint_ir_api_storage)
    implementation(opentaint_ir_storage)

    implementation(KotlinDependency.Libs.kotlin_logging)
    implementation(KotlinDependency.Libs.reflect)

    implementation(Libs.fastutil)

    implementation(Libs.sarif4k)
}


val testSamples by configurations.creating

dependencies {
    testSamples(project("samples"))
}

tasks.withType<Test> {
    maxHeapSize = "4G"

    dependsOn(project("samples").tasks.withType<Jar>())

    doFirst {
        val resolvedTestSamples = testSamples.resolve()
        val testSamplesJar = resolvedTestSamples.single { it.name == "samples.jar" }
        environment("TEST_SAMPLES_JAR", testSamplesJar.absolutePath)
    }
}
