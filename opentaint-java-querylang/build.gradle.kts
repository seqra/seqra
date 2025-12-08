import OpentaintUtilDependency.opentaintUtilJvm
import org.opentaint.common.KotlinDependency
import OpentaintIrDependency.opentaint_ir_core
import OpentaintIrDependency.opentaint_ir_approximations
import OpentaintIrDependency.opentaint_ir_api_storage
import OpentaintIrDependency.opentaint_ir_storage

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
    antlr
}

dependencies {
    implementation("org.opentaint.opentaint-dataflow-core:opentaint-dataflow")
    implementation("org.opentaint.opentaint-dataflow-core:opentaint-jvm-dataflow")
    implementation("org.opentaint.opentaint-configuration-rules:configuration-rules-jvm")
    implementation(opentaintUtilJvm)

    implementation(KotlinDependency.Libs.kaml)

    implementation(opentaint_ir_core)
    implementation(opentaint_ir_approximations)
    implementation(opentaint_ir_api_storage)
    implementation(opentaint_ir_storage)

    implementation(KotlinDependency.Libs.kotlin_logging)

    implementation(Libs.brics_automaton)
    implementation(Libs.jdot)
    antlr(Libs.antlr)
    implementation(Libs.antlr_runtime)

    testCompileOnly(project("samples"))
    testImplementation("org.opentaint.sast:dataflow")
}

val testSamples by configurations.creating

dependencies {
    testSamples(project("samples"))
}

tasks.withType<Test> {
    dependsOn(project("samples").tasks.withType<Jar>())

    val testSamplesJar = testSamples.resolve().single()
    environment("TEST_SAMPLES_JAR", testSamplesJar.absolutePath)

    val configFile = rootProject.layout.projectDirectory.file("config/config.yaml")
    if (configFile.asFile.exists()) {
        environment("TAINT_CONFIGURATION", configFile.asFile.absolutePath)
    }

    jvmArgs = listOf("-Xmx4g")
}

tasks.generateGrammarSource {
    val pkg = "org.opentaint.semgrep.pattern.antlr"
    arguments = arguments + listOf("-package", pkg, "-visitor")
    outputDirectory = outputDirectory.resolve(pkg.split(".").joinToString("/")) // TODO: fix
}

tasks.withType<JavaCompile> {
    options.compilerArgs.remove("-Werror")
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}

tasks.compileTestKotlin {
    dependsOn(tasks.generateTestGrammarSource)
}