import OpentaintConfigDependency.opentaintConfig
import OpentaintUtilDependency.opentaintUtilJvm
import org.opentaint.common.KotlinDependency
import OpentaintIrDependency.opentaint_ir_core
import OpentaintIrDependency.opentaint_ir_approximations
import OpentaintIrDependency.opentaint_ir_api_storage
import OpentaintIrDependency.opentaint_ir_storage
import OpentaintTestUtilDependency.opentaintSastTestUtil
import de.undercouch.gradle.tasks.download.Download

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
    antlr
    id("de.undercouch.download") version "5.6.0"
}

// workaround to remove antlr grammar generation dependencies from runtime classpath
configurations.api.get().let { config ->
    config.setExtendsFrom(config.extendsFrom.filterNot { it == configurations.antlr.get() })
}

dependencies {
    implementation("org.opentaint.opentaint-dataflow-core:opentaint-dataflow")
    implementation("org.opentaint.opentaint-dataflow-core:opentaint-jvm-dataflow")
    implementation("org.opentaint.opentaint-configuration-rules:configuration-rules-jvm")
    implementation(opentaintUtilJvm)
    implementation(opentaintConfig)

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

    testRuntimeOnly(Libs.logback)

    testCompileOnly(project("samples"))
    testImplementation("org.opentaint.sast:dataflow")
    testImplementation(opentaintSastTestUtil)
}

val testSamples by configurations.creating

dependencies {
    testSamples(project("samples"))
}

tasks.withType<Test> {
    dependsOn(project("samples").tasks.withType<Jar>())

    val testSamplesJar = testSamples.resolve().single()
    environment("TEST_SAMPLES_JAR", testSamplesJar.absolutePath)

    jvmArgs = listOf("-Xmx4g")
}

val kotlinGrammar = layout.buildDirectory.dir("kotlin-grammar/src/antlr")
val kotlinGrammarGenerated = layout.buildDirectory.dir("kotlin-grammar/classes/generated")

sourceSets {
    main {
        java {
            srcDir(kotlinGrammarGenerated)
        }
    }
}

val kotlinGrammarVersion = "bf61744020dc46f2d7b8761e35b0c0cb39b3f31a"

val downloadKotlinParserGrammar by tasks.registering(Download::class) {
    val grammarFiles = listOf(
        "KotlinLexer.g4",
        "KotlinParser.g4",
        "UnicodeClasses.g4",
    )

    val antlrGrammarBaseUrl = "https://raw.githubusercontent.com/antlr/grammars-v4/$kotlinGrammarVersion/kotlin/kotlin/"

    src(grammarFiles.map { antlrGrammarBaseUrl + it })
    dest(kotlinGrammar)
    overwrite(false)
}

tasks.generateGrammarSource {
    val pkg = "org.opentaint.semgrep.pattern.antlr"
    arguments = arguments + listOf("-package", pkg, "-visitor")
    outputDirectory = outputDirectory.resolve(pkg.split(".").joinToString("/")) // TODO: fix
}

val generateKotlinGrammarSource by tasks.registering(AntlrTask::class) {
    dependsOn(downloadKotlinParserGrammar)
    source(kotlinGrammar)

    val pkg = "org.opentaint.semgrep.pattern.kotlin.antlr"
    arguments = arguments + listOf("-package", pkg, "-visitor")
    outputDirectory = kotlinGrammarGenerated.get().asFile.resolve(pkg.replace('.', '/'))
}

tasks.withType<JavaCompile> {
    options.compilerArgs.remove("-Werror")
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
    dependsOn(generateKotlinGrammarSource)
}

tasks.compileTestKotlin {
    dependsOn(tasks.generateTestGrammarSource)
    dependsOn(generateKotlinGrammarSource)
}
