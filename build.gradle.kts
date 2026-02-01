import OpentaintIrDependency.opentaint_ir_api_jvm
import OpentaintIrDependency.opentaint_ir_api_storage
import OpentaintIrDependency.opentaint_ir_approximations
import OpentaintIrDependency.opentaint_ir_core
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

tasks.register("buildProjectAnalyzerDocker") {
    dependsOn(projectAnalyzerJar)
    analyzerDockerImage(nameSuffix = "private") {
        projectAnalyzerJar.get().outputs.files.singleFile
    }
}

fun Task.analyzerDockerImage(
    nameSuffix: String,
    analyzerJarProvider: () -> File,
) = this
    .apply { ensureSeEnvInitialized() }
    .doLast {
        val analyzerJar = analyzerJarProvider()

        val contentFiles = mutableListOf(analyzerJar)
        val epVars = mapOf("ANALYZER_JAR_NAME" to analyzerJar.name)

        val rawEnvVars = analyzerEnvironment()
        val envVars = rawEnvVars.mapValues { (_, value) ->
            when (value) {
                is String -> value

                is File -> {
                    contentFiles.add(value)
                    value.name
                }

                else -> error("Unexpected env value: $value")
            }
        }

        buildDockerImage(
            imageName = "analyzer",
            nameSuffix = nameSuffix,
            imageContentFiles = contentFiles,
            entryPointVars = epVars,
            entryPointEnv = envVars,
        )
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

tasks.withType<ProcessResources> {
    val configFile = layout.projectDirectory.file("config/config.yaml")

    doLast {
        check(configFile.asFile.exists()) { "Configuration file not found" }
    }

    from(configFile)
}

fun analyzerEnvironment(): Map<String, Any> {
    val analyzerEnv = mutableMapOf<String, Any>()
    setupOpentaintSeEnvironment(analyzerEnv)
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
