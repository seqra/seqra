import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.opentaint.common.OpentaintIrDependency
import org.opentaint.common.JunitDependencies
import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
    shadowPlugin().apply(false)
}

dependencies {
    implementation("org.opentaint.utils:opentaint-jvm-util:2025.07.15.693dc19")
    implementation("org.opentaint.utils:cli-util:2025.07.15.693dc19")

    implementation("org.opentaint.project:opentaint-project-model:2025.07.15.27da752")

    implementation("org.opentaint.configuration:configuration-rules-jvm:2025.07.15.703f6e5")
    implementation("org.opentaint.opentaint-dataflow-core:opentaint-jvm-dataflow")
    implementation("org.opentaint.sast.se:api")

    implementation("org.opentaint.sast:project")
    implementation("org.opentaint.sast:dataflow")
    implementation(project(":opentaint-java-querylang"))

    implementation(OpentaintIrDependency.Libs.opentaint-ir_api_jvm)
    implementation(OpentaintIrDependency.Libs.opentaint-ir_core)
    implementation(OpentaintIrDependency.Libs.opentaint-ir_approximations)
    implementation(OpentaintIrDependency.Libs.opentaint-ir_api_storage)
    implementation(OpentaintIrDependency.Libs.opentaint-ir_storage)

    implementation(KotlinDependency.Libs.kotlinx_serialization_json)
    implementation(KotlinDependency.Libs.kotlin_logging)
    implementation(KotlinDependency.Libs.kaml)

    implementation(Libs.sarif4k)
    implementation(Libs.clikt)
    implementation(Libs.zt_exec)

    testImplementation(Libs.mockk)
    testImplementation(JunitDependencies.Libs.junit_jupiter_params)
    implementation(Libs.logback)
}

val projectAnalyzerJar = tasks.register<ShadowJar>("projectAnalyzerJar") {
    jarWithDependencies("opentaint-project-analyzer", "org.opentaint.jvm.sast.runner.ProjectAnalyzerRunner")
}

val encryptedConfig = tasks.register<JavaExec>("encryptedConfig") {
    mainClass.set("org.opentaint.jvm.sast.util.ConfigUtils")
    classpath = sourceSets.test.get().runtimeClasspath

    val configFile = layout.projectDirectory.file("config/config.yaml")

    doLast {
        check(configFile.asFile.exists()) { "Configuration file not found" }
    }

    inputs.file(configFile.asFile)

    val result = layout.buildDirectory.file("cfg.enc").get().asFile
    args(configFile.asFile.absolutePath, result.absolutePath)

    outputs.file(result)
}

tasks.register<JavaExec>("runProjectAnalyzer") {
    configureAnalyzer(
        analyzerRunnerClassName = "org.opentaint.jvm.sast.runner.ProjectAnalyzerRunner"
    )
}

val approximations by configurations.creating
val approximationsRepo = "com.github.Opentaint.java-stdlib-approximations"
val approximationsVersion = "7deb55c959"

dependencies {
    approximations(approximationsRepo, "approximations", approximationsVersion)
}

fun JavaExec.configureAnalyzer(analyzerRunnerClassName: String) {
    dependsOn(encryptedConfig)

    mainClass.set(analyzerRunnerClassName)
    classpath = sourceSets.main.get().runtimeClasspath

    val configFile = encryptedConfig.get().outputs.files.singleFile
    val envVars = mutableMapOf("opentaint_taint_config_path" to configFile)

    doFirst {
        val opentaintApiJarPath = tryResolveDependency("org.opentaint.jvm:api")
        if (opentaintApiJarPath != null) {
            val opentaintApproximationJarPath = approximations.resolvedConfiguration.files.single()

            envVars["opentaint.jvm.api.jar.path"] = opentaintApiJarPath.single()
            envVars["opentaint.jvm.approximations.jar.path"] = opentaintApproximationJarPath
        }

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
) = dependsOn(encryptedConfig)
    .doLast {
        val analyzerVersion = project.findProperty("analyzerVersion") ?: "latest"
        val analyzerJar = analyzerJarProvider()

        val configFile = encryptedConfig.get().outputs.files.singleFile

        val contentFiles = mutableListOf(analyzerJar, configFile)

        val envVars = mutableMapOf(
            "ANALYZER_JAR_NAME" to analyzerJar.name,
            "TAINT_CONFIG" to configFile.name,
            "SARIF_Opentaint_ORGANIZATION" to "Explyt",
            "SARIF_Opentaint_VERSION" to "$analyzerVersion",
        )

        val opentaintApiJarPath = tryResolveDependency("org.opentaint.jvm:api")
        if (opentaintApiJarPath != null) {
            val opentaintApproximationJarPath = approximations.resolvedConfiguration.files.single()

            envVars["JVM_API_JAR"] = opentaintApiJarPath.single().name
            envVars["JVM_APPROXIMATIONS_JAR"] = opentaintApproximationJarPath.name

            contentFiles.addAll(opentaintApiJarPath)
            contentFiles.add(opentaintApproximationJarPath)
        }

        buildDockerImage(
            imageName = "analyzer",
            nameSuffix = nameSuffix,
            imageContentFiles = contentFiles,
            entryPointVars = envVars
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

fun tryResolveDependency(dependency: String): Set<File>? {
    val auxConfig = configurations.create("try-resolve-config-aux")
    auxConfig.dependencies.add(dependencies.create(dependency))

    return try {
        auxConfig.resolve()
    } catch (ex: ResolveException) {
        null
    }  finally {
        configurations.remove(auxConfig)
    }
}
