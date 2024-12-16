val kotlinVersion: String by rootProject
val coroutinesVersion: String by rootProject

dependencies {
    api(project(":opentaint-ir-core"))
    api(project(":opentaint-ir-api"))

    testImplementation(project(":opentaint-ir-testing"))
}