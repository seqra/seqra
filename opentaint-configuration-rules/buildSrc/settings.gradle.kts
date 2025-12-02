includeBuild("../../opentaint-common-build") {
    dependencySubstitution {
        substitute(module("org.opentaint:opentaint-common-build")).using(project(":"))
    }
}
