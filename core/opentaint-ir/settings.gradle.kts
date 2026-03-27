rootProject.name = "opentaint-ir"

include("opentaint-ir-api-common")
include("opentaint-ir-api-jvm")
include("opentaint-ir-api-storage")
include("opentaint-ir-core")
include("opentaint-ir-storage")
include("opentaint-ir-approximations")

include("python:opentaint-ir-api-python")
include("python:opentaint-ir-impl-python")
include("python:opentaint-ir-test-python")

include("go:go-ir-api")
include("go:go-ir-client")
include("go:go-ir-codegen")
include("go:go-ir-tests")
