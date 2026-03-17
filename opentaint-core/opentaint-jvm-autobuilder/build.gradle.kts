import OpentaintProjectDependency.opentaintProject
import OpentaintUtilDependency.opentaintUtilCli
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.opentaint.common.KotlinDependency

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
    shadowPlugin().apply(false)
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
    }
    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_1_8
        }
    }
}

dependencies {
    implementation(opentaintProject)
    implementation(opentaintUtilCli)

    implementation(KotlinDependency.Libs.kotlinx_serialization_json)
    implementation(KotlinDependency.Libs.kotlin_logging)

    implementation(Libs.zt_exec)
    implementation(Libs.slf4j_api)
    implementation(Libs.logback)
}

val projectAutoBuilderJar = tasks.register<ShadowJar>("projectAutoBuilderJar") {
    jarWithDependencies("opentaint-project-auto-builder", "org.opentaint.project.ProjectAutoBuilder")
}

tasks.register<JavaExec>("runAutoBuilder") {
    mainClass.set("org.opentaint.project.ProjectAutoBuilder")
    classpath = sourceSets.main.get().runtimeClasspath
    jvmArgs = listOf("-Xmx8g")
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
