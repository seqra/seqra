import OpentaintConfigDependency.opentaintJavaConfig
import OpentaintIrDependency.opentaint_ir_api_storage
import OpentaintIrDependency.opentaint_ir_approximations
import OpentaintIrDependency.opentaint_ir_core
import OpentaintIrDependency.opentaint_ir_storage
import OpentaintUtilDependency.opentaintUtilJvm
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
    antlr
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
    implementation(opentaintJavaConfig)

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
    testImplementation(rootProject)
}

val testSamples by configurations.creating

dependencies {
    testSamples(project("samples"))
}

tasks.withType<Test> {
    dependsOn(project("samples").tasks.withType<Jar>())

    val bidiTestMode = providers.environmentVariable("OPENTAINT_TEST_BIDI").orElse("false")
    inputs.property("bidiTestMode", bidiTestMode)
    environment("OPENTAINT_TEST_BIDI", bidiTestMode.get())

    val testSamplesJar = testSamples.resolve().single()
    environment("TEST_SAMPLES_JAR", testSamplesJar.absolutePath)

    jvmArgs = listOf("-Xmx4g")
}

val kotlinGrammar = layout.projectDirectory.dir("src/main/antlr-kotlin")
val kotlinGrammarGenerated = layout.buildDirectory.dir("kotlin-grammar/classes/generated")

sourceSets {
    main {
        java {
            srcDir(kotlinGrammarGenerated)
        }
    }
}

tasks.generateGrammarSource {
    val pkg = "org.opentaint.semgrep.pattern.antlr"
    arguments = arguments + listOf("-package", pkg, "-visitor")
    outputDirectory = outputDirectory.resolve(pkg.split(".").joinToString("/")) // TODO: fix
}

val generateKotlinGrammarSource by tasks.registering(AntlrTask::class) {
    source(kotlinGrammar)

    val pkg = "org.opentaint.semgrep.pattern.kotlin.antlr"
    arguments = arguments + listOf("-package", pkg, "-visitor")
    outputDirectory = kotlinGrammarGenerated.get().asFile.resolve(pkg.replace('.', '/'))
}

tasks.withType<JavaCompile> {
    options.compilerArgs.remove("-Werror")
}

tasks.named<JavaCompile>("compileTestJava") {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
}

tasks.named<KotlinCompile>("compileTestKotlin") {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
    dependsOn(generateKotlinGrammarSource)
}

tasks.compileTestKotlin {
    dependsOn(tasks.generateTestGrammarSource)
    dependsOn(generateKotlinGrammarSource)
}
