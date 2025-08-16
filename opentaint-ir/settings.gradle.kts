rootProject.name = "opentaint-ir"

plugins {
    id("com.gradle.develocity") version("3.18.2")
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "1.1.11"
}

develocity {
    buildScan {
        // Accept the term of use for the build scan plugin:
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")

        // Publish build scans on-demand, when `--scan` option is provided:
        publishing.onlyIf { false }
    }
}

gitHooks {
    preCommit {
        from(file("pre-commit"))
    }
    createHooks(true)
}

include("opentaint-ir-api-common")
include("opentaint-ir-api-jvm")
include("opentaint-ir-api-storage")
include("opentaint-ir-core")
include("opentaint-ir-storage")
include("opentaint-ir-examples")
include("opentaint-ir-benchmarks")
include("opentaint-ir-approximations")
include("opentaint-ir-taint-configuration")
include("opentaint-ir-frontend")
