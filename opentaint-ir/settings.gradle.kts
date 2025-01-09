rootProject.name = "opentaint-ir"

plugins {
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "1.0.25"
}

gitHooks {
    preCommit {
        // Content can be added at the bottom of the script
        from(file("pre-commit").toURI().toURL())
    }
    createHooks() // actual hooks creation
}

include("opentaint-ir-api")
include("opentaint-ir-core")
include("opentaint-ir-analysis")
include("opentaint-ir-examples")
include("opentaint-ir-benchmarks")
