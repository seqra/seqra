plugins {
    java
}

val bidiJava17 = sourceSets.create("bidiJava17")

tasks {
    withType<JavaCompile> {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
        options.compilerArgs.add("-g")
    }
}

tasks.named<JavaCompile>(bidiJava17.compileJavaTaskName) {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
    options.release.set(17)
}

tasks.jar {
    from(bidiJava17.output)
    from(sourceSets.main.get().allSource) {
        include("**/*.java")
    }
    from(bidiJava17.allSource) {
        include("**/*.java")
    }
}
