plugins {
    id("opentaint.kotlin-conventions")
    id("org.jetbrains.kotlin.plugin.serialization") version Versions.kotlin
    antlr
}

dependencies {
    implementation(project(":opentaint-dataflow"))
    implementation(project(":opentaint-jvm-dataflow"))
    implementation(project(":opentaint-jvm-dataflow:opentaint-jvm-dataflow-configuration"))

    testImplementation(project(":opentaint-jvm"))

    implementation("com.charleskorn.kaml:kaml:0.73.0")

    implementation(Libs.opentaint-ir_core)
    implementation(Libs.opentaint-ir_approximations)
    implementation(Libs.opentaint-ir_api_storage)
    implementation(Libs.opentaint-ir_storage)

    implementation("dk.brics.automaton:automaton:1.11-8")

    val jdot = dep(
        group = "info.leadinglight",
        name = "jdot",
        version = "1.0"
    )
    implementation(jdot)
    antlr("org.antlr:antlr4:4.9.3")
    implementation("org.antlr:antlr4-runtime:4.9.3")
}

val testSamples by configurations.creating

dependencies {
    testSamples(project("samples"))
}

tasks.withType<Test> {
    dependsOn(project("samples").tasks.withType<Jar>())

    val testSamplesJar = testSamples.resolve().single()
    environment("TEST_SAMPLES_JAR", testSamplesJar.absolutePath)

    val configFile = project(":opentaint-jvm").layout.projectDirectory.file("config/config.yaml")
    if (configFile.asFile.exists()) {
        environment("TAINT_CONFIGURATION", configFile.asFile.absolutePath)
    }

    jvmArgs = listOf("-Xmx8g")
}

tasks.generateGrammarSource {
    val pkg = "org.opentaint.semgrep.pattern.antlr"
    arguments = arguments + listOf("-package", pkg, "-visitor")
    outputDirectory = outputDirectory.resolve(pkg.split(".").joinToString("/")) // TODO: fix
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}

tasks.compileTestKotlin {
    dependsOn(tasks.generateTestGrammarSource)
}