import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target
import java.nio.file.Paths

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(Libs.jooq_meta)
        classpath(Libs.jooq_meta_extensions)
        classpath(Libs.jooq_codegen)
        classpath(Libs.jooq_kotlin)
        // classpath(Libs.postgresql)
        // classpath(Libs.hikaricp)
        // classpath(Libs.sqlite)
    }
}

plugins {
    `java-test-fixtures`
}

kotlin.sourceSets["main"].kotlin {
    srcDir("src/main/jooq")
}

dependencies {
    implementation(project(":opentaint-ir-api"))

    implementation(Libs.kotlin_logging)
    implementation(Libs.kotlinx_metadata_jvm)
    implementation(Libs.kotlinx_serialization_cbor)
    implementation(Libs.jdot)
    implementation(Libs.guava)
    implementation(Libs.postgresql)
    implementation(Libs.hikaricp)
    implementation(Libs.sqlite)

    testImplementation(Libs.javax_activation)
    testImplementation(Libs.javax_mail)
    testImplementation(Libs.joda_time)
    testImplementation(Libs.slf4j_simple)
    // testImplementation(files("src/test/resources/samples"))

    testFixturesImplementation(project(":opentaint-ir-api"))
    testFixturesImplementation(kotlin("reflect"))
    testFixturesImplementation(platform(Libs.junit_bom))
    testFixturesImplementation(Libs.junit_jupiter)
    testFixturesImplementation(Libs.guava)
    testFixturesImplementation(Libs.mockito_core)
    testFixturesImplementation(Libs.jetbrains_annotations)
    testFixturesImplementation(Libs.kotlinx_coroutines_core)
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
                                .withPackageName("org.opentaint.ir.impl.storage.jooq")
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
