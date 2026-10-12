import OpentaintIrDependency.opentaint_ir_approximations

// This convention compiles each built-in model module separately.
// The module build file specifies its library dependencies.

plugins {
    java
    `java-library`
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
    sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:none")
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    compileOnly(opentaint_ir_approximations)

    // Each model module uses the support types from the core module.
    if (path != ApproximationModules.CORE) {
        compileOnly(project(ApproximationModules.CORE))
    }
}
