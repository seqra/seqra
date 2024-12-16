import de.undercouch.gradle.tasks.download.Download
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.*
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Target
import java.nio.file.Paths

val asmVersion: String by rootProject
val kotlinVersion: String by rootProject
val coroutinesVersion: String by rootProject
val kmetadataVersion: String by rootProject
val jooqVersion: String by rootProject

buildscript {
    val jooqVersion: String by rootProject
    dependencies {
        classpath(group = "org.jooq", name = "jooq-meta", version = jooqVersion)
        classpath(group = "org.jooq", name = "jooq-meta-extensions", version = jooqVersion)
        classpath(group = "org.jooq", name = "jooq-codegen", version = jooqVersion)
        classpath(group = "org.jooq", name = "jooq-kotlin", version = jooqVersion)
        classpath(group = "org.postgresql", name = "postgresql", version = "42.5.1")
        classpath(group = "org.xerial", name = "sqlite-jdbc", version = "3.39.2.1")
        classpath(group = "com.zaxxer", name = "HikariCP", version = "5.0.1")
    }
}

plugins {
    id("de.undercouch.download") version "5.3.0"
    `java-test-fixtures`
}

kotlin.sourceSets["main"].kotlin {
    srcDir("src/main/jooq")
}

dependencies {
    implementation(project(":opentaint-ir-api"))

    api(group = "io.github.microutils", name = "kotlin-logging", version = "1.8.3")
    api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-jdk8", version = kotlinVersion)
    api(group = "org.jetbrains.kotlin", name = "kotlin-reflect", version = kotlinVersion)
    api(group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-cbor", version = "1.3.3")
    api(group = "info.leadinglight", name = "jdot", version = "1.0")

    implementation(group = "org.postgresql", name = "postgresql", version = "42.5.1")
    implementation(group = "com.zaxxer", name = "HikariCP", version = "5.0.1")
    implementation(group = "org.xerial", name = "sqlite-jdbc", version = "3.39.2.1")

    implementation(group = "com.google.guava", name = "guava", version = "31.1-jre")
    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-metadata-jvm", version = kmetadataVersion)

    testFixturesImplementation(project(":opentaint-ir-api"))

    testFixturesImplementation(platform("org.junit:junit-bom:5.9.0"))
    testFixturesImplementation(group = "org.jetbrains.kotlin", name = "kotlin-reflect", version = kotlinVersion)
    testFixturesImplementation(group = "org.junit.jupiter", name = "junit-jupiter")
    testFixturesImplementation(group = "com.google.guava", name = "guava", version = "31.1-jre")
    testFixturesImplementation(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = coroutinesVersion)
    testFixturesImplementation(group = "org.mockito", name = "mockito-core", version = "4.8.0")
    testFixturesImplementation(group = "org.jetbrains", name = "annotations", version = "20.1.0")
    testImplementation(group = "org.opentaint.soot", name = "soot-opentaint-fork", version = "4.4.0-FORK-2")

}

tasks.register<Download>("downloadIdeaCommunity") {
    src(rootProject.properties.get("intellij_community_url") as String)
    dest("idea-community/idea-community.zip")
}

tasks.register<Copy>("downloadAndUnzipIdeaCommunity") {
    dependsOn("downloadIdeaCommunity")
    val downloadIdeaCommunity by tasks.getting(Download::class)

    from(zipTree(downloadIdeaCommunity.dest))
    into("idea-community/unzip")
}

benchmark {
    configurations {
        named("main") {
            include("JirdbLifeCycleBenchmarks")
            include("RestoreJirdbBenchmark")
        }
        register("jIRdb") {
            include("JirdbBenchmarks")
        }
        register("soot") {
            include("SootBenchmarks")
        }
        register("awaitBackground") {
            include("JirdbJvmBackgroundBenchmarks")
            include("JirdbAllClasspathBackgroundBenchmarks")
            include("JirdbIdeaBackgroundBenchmarks")
        }
    }
}

val benchmarkTasks = listOf("testJirdbBenchmark", "testSootBenchmark", "testAwaitBackgroundBenchmark")
tasks.matching { benchmarkTasks.contains(it.name) }.configureEach {
    dependsOn("downloadAndUnzipIdeaCommunity")
}

tasks.register("generateSqlScheme") {
    val databaseLocation = project.properties["database_location"]
    if (databaseLocation != null) {
        val url = "jdbc:sqlite:file:$databaseLocation"
        val driver = "org.sqlite.JDBC"
        GenerationTool.generate(
            Configuration()
                .withJdbc(
                    Jdbc()
                        .withDriver(driver)
                        .withUrl(url)
                )
                .withGenerator(
                    Generator()
                        .withName("org.jooq.codegen.KotlinGenerator")
                        .withDatabase(Database())
                        .withGenerate(
                            Generate()
                                .withDeprecationOnUnknownTypes(false)
                        )
                        .withTarget(
                            Target()
                                .withPackageName("org.opentaint.opentaint-ir.impl.storage.jooq")
                                .withDirectory(project.file("src/main/jooq").absolutePath)
                        )
                )
        )
    }
}

tasks.register<JavaExec>("generateDocSvgs") {
    dependsOn("testClasses")
    mainClass.set("org.opentaint.jIRdb.impl.cfg.IRSvgGeneratorKt")
    classpath = sourceSets.test.get().runtimeClasspath
    val svgDocs = Paths.get(rootDir.absolutePath, "docs", "svg").toFile()
    args = listOf(svgDocs.absolutePath)
}
