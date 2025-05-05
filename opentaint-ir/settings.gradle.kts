rootProject.name = "opentaint-ir"

plugins {
    `gradle-enterprise`
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "1.1.11"
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}

gitHooks {
    preCommit {
        from(file("pre-commit"))
    }
    createHooks(true)
}

include("opentaint-ir-api-core")
include("opentaint-ir-api-jvm")
include("opentaint-ir-core")
include("opentaint-ir-analysis")
include("opentaint-ir-examples")
include("opentaint-ir-benchmarks")
include("opentaint-ir-cli")
include("opentaint-ir-approximations")
include("opentaint-ir-taint-configuration")
