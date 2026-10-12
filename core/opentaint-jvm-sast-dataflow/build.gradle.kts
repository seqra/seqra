import OpentaintUtilDependency.opentaintUtilJvm
import org.opentaint.common.KotlinDependency
import OpentaintIrDependency.opentaint_ir_api_jvm
import OpentaintIrDependency.opentaint_ir_api_storage
import OpentaintIrDependency.opentaint_ir_core
import OpentaintIrDependency.opentaint_ir_storage
import OpentaintIrDependency.opentaint_ir_approximations

plugins {
    id("kotlin-conventions")
}

dependencies {
    implementation("org.opentaint.opentaint-dataflow-core:opentaint-jvm-dataflow")
    implementation("org.opentaint.opentaint-configuration-rules:configuration-rules-jvm")
    implementation(opentaintUtilJvm)

    implementation(opentaint_ir_api_jvm)
    implementation(opentaint_ir_core)
    implementation(opentaint_ir_approximations)
    implementation(opentaint_ir_api_storage)
    implementation(opentaint_ir_storage)

    implementation(Libs.fastutil)
    implementation(KotlinDependency.Libs.kotlin_logging)
}

val approximationsConfig by configurations.creating

// Collect classes from all built-in model modules.
val approximationModules = childProjects.keys.filter { it.startsWith(ApproximationModules.PROJECT_PREFIX) }

dependencies {
    approximationModules.forEach { approximationsConfig(project(":$it")) }
}

// Build the API jar for external model projects.
// It contains the approximation annotations and the core support types.
val approximationsApiCore by configurations.creating
val approximationsApiAnnotations by configurations.creating

dependencies {
    approximationsApiCore(project(ApproximationModules.CORE))
    approximationsApiAnnotations(opentaint_ir_approximations)
}

val approximationsApiJar by tasks.registering(Jar::class) {
    archiveFileName = ApproximationModules.API_JAR_NAME

    from(approximationsApiCore.elements.map { files -> files.map { zipTree(it) } }) {
        include("**/*.class")
    }
    from(approximationsApiAnnotations.elements.map { files -> files.map { zipTree(it) } }) {
        include("org/opentaint/ir/approximation/annotation/**/*.class")
    }

    duplicatesStrategy = DuplicatesStrategy.FAIL
}

tasks.withType<ProcessResources> {
    from(approximationsConfig.elements.map { files -> files.map { zipTree(it) } }) {
        include("**/*.class")
        into("opentaint-dataflow-approximations")

        // Do not let one model class replace another model class.
        duplicatesStrategy = DuplicatesStrategy.FAIL
    }

    from(approximationsApiJar) {
        into(ApproximationModules.API_JAR_RESOURCE_DIR)
    }
}
