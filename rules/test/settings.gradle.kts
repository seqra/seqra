rootProject.name = "seqra-builtin-rules-test"

includeBuild("../../core/opentaint-sast-test-util") {
    dependencySubstitution {
        substitute(module("org.opentaint.sast-test-util:opentaint-sast-test-util")).using(project(":"))
    }
}
