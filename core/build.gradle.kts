import OpentaintConfigDependency.opentaintConfig
import OpentaintIrDependency.opentaint_ir_api_go
import OpentaintIrDependency.opentaint_ir_api_jvm
import OpentaintIrDependency.opentaint_ir_api_python
import OpentaintIrDependency.opentaint_ir_api_storage
import OpentaintIrDependency.opentaint_ir_approximations
import OpentaintIrDependency.opentaint_ir_core
import OpentaintIrDependency.opentaint_ir_core_go
import OpentaintIrDependency.opentaint_ir_core_python
import OpentaintIrDependency.opentaint_ir_storage
import OpentaintProjectDependency.opentaintProject
import OpentaintUtilDependency.opentaintUtilCli
import OpentaintUtilDependency.opentaintUtilJvm
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.opentaint.common.JunitDependencies
import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
    shadowPlugin().apply(false)
}

dependencies {
    implementation(opentaintUtilJvm)
    implementation(opentaintUtilCli)
    implementation(opentaintProject)
    implementation(opentaintConfig)

    implementation("org.opentaint.opentaint-configuration-rules:configuration-rules-jvm")
    implementation("org.opentaint.opentaint-dataflow-core:opentaint-jvm-dataflow")
    implementation("org.opentaint.sast.se:api")

    implementation("org.opentaint.sast:project")
    implementation("org.opentaint.sast:dataflow")
    implementation(project(":opentaint-java-querylang"))

    implementation(opentaint_ir_api_jvm)
    implementation(opentaint_ir_core)
    implementation(opentaint_ir_approximations)
    implementation(opentaint_ir_api_storage)
    implementation(opentaint_ir_storage)

    implementation(KotlinDependency.Libs.kotlinx_serialization_json)
    implementation(KotlinDependency.Libs.kotlin_logging)
    implementation(KotlinDependency.Libs.kaml)

    implementation(Libs.sarif4k)
    implementation(Libs.clikt)
    implementation(Libs.zt_exec)
    implementation(Libs.antlr_runtime)

    testImplementation(Libs.mockk)
    testImplementation(JunitDependencies.Libs.junit_jupiter_params)
    implementation(Libs.logback)
    implementation(Libs.jdot)

    testCompileOnly(project("samples"))

    testImplementation(opentaint_ir_api_python)
    testImplementation(opentaint_ir_core_python)
    testImplementation(opentaint_ir_api_go)
    testImplementation(opentaint_ir_core_go)
    testImplementation("org.opentaint.opentaint-dataflow-core:opentaint-python-dataflow")
}

val testSamples by configurations.creating

dependencies {
    testSamples(project("samples"))
}

tasks.withType<Test> {
    dependsOn(project("samples").tasks.withType<Jar>())
    ensurePirEnvInitialized()

    doFirst {
        val resolvedTestSamples = testSamples.resolve()
        val testSamplesJar = resolvedTestSamples.single { it.name == "samples.jar" }
        val testDependencies = resolvedTestSamples.filter { it.name != "samples.jar" }
        environment("TEST_SAMPLES_JAR", testSamplesJar.absolutePath)
        environment("TEST_DEPENDENCIES_JAR", testDependencies.joinToString(File.pathSeparator) { it.absolutePath })

        // Ant benchmark samples JAR
        val antBenchmarkJar = project("samples").tasks.named<Jar>("antBenchmarkJar").get().archiveFile.get().asFile
        environment("ANT_BENCHMARK_SAMPLES_JAR", antBenchmarkJar.absolutePath)
        val pirEnv = pirEnvironment()
        pirEnv.forEach { (key, value) ->
            environment(key, value)
        }
    }
}

val projectAnalyzerJar = tasks.register<ShadowJar>("projectAnalyzerJar") {
    jarWithDependencies("opentaint-project-analyzer", "org.opentaint.jvm.sast.runner.ProjectAnalyzerRunner")
}

tasks.register<JavaExec>("runProjectAnalyzer") {
    configureAnalyzer(
        analyzerRunnerClassName = "org.opentaint.jvm.sast.runner.ProjectAnalyzerRunner"
    )
}

fun JavaExec.configureAnalyzer(analyzerRunnerClassName: String) {
    mainClass.set(analyzerRunnerClassName)
    classpath = sourceSets.main.get().runtimeClasspath

    ensureSeEnvInitialized()

    doFirst {
        val envVars = analyzerEnvironment()
        envVars.forEach { (key, value) ->
            environment(key, value)
        }
    }

    systemProperty("org.opentaint.ir.impl.storage.defaultBatchSize", 2000)
    systemProperty("jdk.util.jar.enableMultiRelease", false)
    jvmArgs = listOf("-Xmx8g")
}

fun JavaExec.addEnvIfExists(envName: String, path: String) {
    val file = File(path)
    if (!file.exists()) {
        println("Not found $envName at $path")
        return
    }

    environment(envName, file.absolutePath)
}

fun ShadowJar.jarWithDependencies(name: String, mainClass: String) {
    duplicatesStrategy = DuplicatesStrategy.WARN
    archiveBaseName.set(name)

    manifest {
        attributes(mapOf("Main-Class" to mainClass))
    }

    configurations = listOf(project.configurations.runtimeClasspath.get())
    mergeServiceFiles()

    with(tasks.jar.get() as CopySpec)
}

fun analyzerEnvironment(): Map<String, Any> {
    val analyzerEnv = mutableMapOf<String, Any>()
    setupOpentaintSeEnvironment(analyzerEnv)
    return analyzerEnv
}

fun pirEnvironment(): Map<String, Any> {
    val pirEnv = mutableMapOf<String, Any>()
    setupOpentaintPirEnvironment(pirEnv)
    return pirEnv
}

@Suppress("UNCHECKED_CAST")
fun setupOpentaintSeEnvironment(analyzerEnv: MutableMap<String, Any>) {
    val initializer = findOpentaintSeEnvInitializer() ?: return
    val seEnv = initializer.extra.get("opentaint.se.analyzer.env") as Map<String, Any>
    analyzerEnv += seEnv
}


val opentaintPirEnvKey = "opentaint.pir.env"

@Suppress("UNCHECKED_CAST")
fun setupOpentaintPirEnvironment(pirEnv: MutableMap<String, Any>) {
    val initializer = findOpentaintPirEnvInitializer() ?: return
    val env = initializer.extra.get(opentaintPirEnvKey) as Map<String, Any>
    pirEnv += env
}

fun Task.ensureSeEnvInitialized() {
    val initializer = findOpentaintSeEnvInitializer() ?: return
    dependsOn(initializer)
}

fun Task.ensurePirEnvInitialized() {
    val initializer = findOpentaintPirEnvInitializer() ?: return
    dependsOn(initializer)
}

fun findOpentaintSeEnvInitializer(): Task? {
    val seProject = gradle.includedBuilds.find { it.name == "opentaint-jvm-sast-se" } ?: return null
    return seProject.resolveIncludedProjectTask(":setupAnalyzerEnvironment")
}

fun findOpentaintPirEnvInitializer(): Task? {
    val pirProject = gradle.includedBuilds.find { it.name == "opentaint-ir" } ?: return null
    return pirProject.resolveIncludedProjectTask(":python:setupPirEnvironment")
}

tasks.withType<Test> {
    maxHeapSize = "4G"
}
