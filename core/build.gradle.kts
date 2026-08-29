import OpentaintConfigDependency.opentaintJavaConfig
import OpentaintConfigDependency.opentaintGoConfig
import OpentaintIrDependency.opentaint_ir_api_go
import OpentaintIrDependency.opentaint_ir_api_jvm
import OpentaintIrDependency.opentaint_ir_api_storage
import OpentaintIrDependency.opentaint_ir_approximations
import OpentaintIrDependency.opentaint_ir_core
import OpentaintIrDependency.opentaint_ir_core_go
import OpentaintIrDependency.opentaint_ir_storage
import OpentaintProjectDependency.opentaintProject
import OpentaintUtilDependency.opentaintUtilCli
import OpentaintUtilDependency.opentaintUtilJvm
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.opentaint.common.JunitDependencies
import org.opentaint.common.KotlinDependency
import org.opentaint.common.resolveIncludedProjectTask
import org.opentaint.common.ensureGoEnvInitialized
import org.opentaint.common.goEnvironment
import org.opentaint.common.setupOpentaintGoEnvironment

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
    shadowPlugin().apply(false)
}

dependencies {
    implementation(opentaintUtilJvm)
    implementation(opentaintUtilCli)
    implementation(opentaintProject)
    implementation(opentaintJavaConfig)
    implementation(opentaintGoConfig)

    implementation("org.opentaint.opentaint-configuration-rules:configuration-rules-jvm")
    implementation("org.opentaint.opentaint-configuration-rules:configuration-rules-go")
    implementation("org.opentaint.opentaint-dataflow-core:opentaint-jvm-dataflow")
    implementation("org.opentaint.sast.se:api")

    implementation("org.opentaint.sast:project")
    implementation("org.opentaint.sast:dataflow")
    implementation(project(":opentaint-java-querylang"))
    implementation(project(":opentaint-go-querylang"))

    implementation(opentaint_ir_api_jvm)
    implementation(opentaint_ir_core)
    implementation(opentaint_ir_approximations)
    implementation(opentaint_ir_api_storage)
    implementation(opentaint_ir_storage)

    implementation(KotlinDependency.Libs.kotlinx_serialization_json)
    implementation(KotlinDependency.Libs.kotlin_logging)
    implementation(KotlinDependency.Libs.kaml)

    implementation(Libs.fastutil)

    implementation(Libs.sarif4k)
    implementation(Libs.clikt)
    implementation(Libs.zt_exec)
    implementation(Libs.antlr_runtime)

    testImplementation(Libs.mockk)
    testImplementation(JunitDependencies.Libs.junit_jupiter_params)
    implementation(Libs.logback)
    implementation(Libs.jdot)

    testCompileOnly(project("samples"))

    implementation(opentaint_ir_api_go)
    implementation(opentaint_ir_core_go)
    implementation("org.opentaint.opentaint-dataflow-core:opentaint-go-dataflow")
}

val testSamples by configurations.creating

dependencies {
    testSamples(project("samples"))
}

tasks.withType<Test> {
    dependsOn(project("samples").tasks.withType<Jar>())
    ensureGoEnvInitialized()
    systemProperty(
        "opentaint.go.models.root",
        layout.projectDirectory.dir("../model/go/dataflow").asFile.absolutePath,
    )

    doFirst {
        val resolvedTestSamples = testSamples.resolve()
        val testSamplesJar = resolvedTestSamples.single { it.name == "samples.jar" }
        val testDependencies = resolvedTestSamples.filter { it.name != "samples.jar" }
        environment("TEST_SAMPLES_JAR", testSamplesJar.absolutePath)
        environment("TEST_DEPENDENCIES_JAR", testDependencies.joinToString(File.pathSeparator) { it.absolutePath })

        goEnvironment().forEach { (key, value) -> environment(key, value) }
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
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
    ensureGoEnvInitialized()

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
    setupOpentaintGoEnvironment(analyzerEnv)
    return analyzerEnv
}

@Suppress("UNCHECKED_CAST")
fun setupOpentaintSeEnvironment(analyzerEnv: MutableMap<String, Any>) {
    val initializer = findOpentaintSeEnvInitializer() ?: return
    val seEnv = initializer.extra.get("opentaint.se.analyzer.env") as Map<String, Any>
    analyzerEnv += seEnv
}

fun Task.ensureSeEnvInitialized() {
    val initializer = findOpentaintSeEnvInitializer() ?: return
    dependsOn(initializer)
}

fun findOpentaintSeEnvInitializer(): Task? {
    val seProject = gradle.includedBuilds.find { it.name == "opentaint-jvm-sast-se" } ?: return null
    return seProject.resolveIncludedProjectTask(":setupAnalyzerEnvironment")
}

tasks.withType<Test> {
    maxHeapSize = "4G"
}
