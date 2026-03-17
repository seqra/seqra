import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target
import org.opentaint.common.JunitDependencies
import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(Libs.jooq_meta)
        classpath(Libs.jooq_meta_extensions)
        classpath(Libs.jooq_codegen)
        classpath(Libs.jooq_kotlin)
        classpath(Libs.sqlite)
    }
}

kotlin.sourceSets["main"].kotlin {
    srcDir("src/main/jooq")
    srcDir("src/main/ers/jooq")
}

dependencies {
    api(project(":opentaint-ir-api-jvm"))
    api(project(":opentaint-ir-storage"))
    compileOnly(Libs.jooq)
    compileOnly(Libs.sqlite)
    compileOnly(Libs.hikaricp)

    implementation(KotlinDependency.Libs.kotlin_logging)
    compileOnly(KotlinDependencyExt.Libs.kotlin_metadata_jvm_compile)
    runtimeOnly(KotlinDependencyExt.Libs.kotlin_metadata_jvm_runtime) {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation(KotlinDependency.Libs.kotlinx_serialization_core)
    implementation(Libs.jdot)
    implementation(Libs.guava)
    implementation(Libs.xodusUtils)

    testImplementation(testFixtures(project(":opentaint-ir-storage")))
    testImplementation(TestDependencies.Libs.javax_activation)
    testImplementation(TestDependencies.Libs.javax_mail)
    testImplementation(TestDependencies.Libs.joda_time)
    testImplementation(TestDependencies.Libs.slf4j_simple)
    testImplementation(Libs.hikaricp)
    testImplementation(Libs.xodusEnvironment)
    testImplementation(Libs.lmdb_java)
    testImplementation(Libs.rocks_db)

    testFixturesApi(Libs.jooq)
    testFixturesApi(Libs.sqlite)
    testFixturesApi(Libs.hikaricp)
    testFixturesApi(project(":opentaint-ir-storage"))
    testFixturesImplementation(kotlin("reflect"))
    testFixturesImplementation(platform(JunitDependencies.Libs.junit_bom))
    testFixturesImplementation(JunitDependencies.Libs.junit_jupiter)
    testFixturesImplementation(Libs.guava)
    testFixturesImplementation(TestDependencies.Libs.jetbrains_annotations)
    testFixturesImplementation(KotlinDependency.Libs.kotlin_logging)
    testFixturesImplementation(KotlinDependency.Libs.kotlinx_coroutines_core)
    testFixturesImplementation(TestDependencies.Libs.jgit_test_only_lib)
    testFixturesImplementation(TestDependencies.Libs.commons_compress_test_only_lib)
}

tasks {
    register("generateSqlScheme") {
        doLast {
            generateSqlScheme(
                dbLocation = "src/main/resources/sqlite/empty.db",
                sourceSet = "src/main/jooq",
                packageName = "org.opentaint.ir.impl.storage.jooq"
            )
        }
    }

    register("generateErsSqlScheme") {
        doLast {
            generateSqlScheme(
                dbLocation = "src/main/resources/ers/sqlite/empty.db",
                sourceSet = "src/main/ers/jooq",
                packageName = "org.opentaint.ir.impl.storage.ers.jooq"
            )
        }
    }

    processResources {
        filesMatching("**/*.properties") {
            expand("version" to project.version)
        }
    }
}

fun generateSqlScheme(
    dbLocation: String,
    sourceSet: String,
    packageName: String
) {
    val url = "jdbc:sqlite:file:${project.projectDir}/$dbLocation"
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
                            .withPackageName(packageName)
                            .withDirectory(project.file(sourceSet).absolutePath)
                    )
            )
    )
}