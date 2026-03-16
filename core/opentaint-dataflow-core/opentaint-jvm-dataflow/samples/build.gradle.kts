plugins {
    java
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
        options.compilerArgs.add("-g")
    }
}

tasks.jar {
    from(sourceSets.main.get().allSource) {
        include("**/*.java")
    }
}
