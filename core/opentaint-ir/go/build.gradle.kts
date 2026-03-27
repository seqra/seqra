import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Exec

plugins {
    id("kotlin-conventions")
}

// ─── Go server environment setup ────────────────────────────────────
val goEnvironmentExtraKey = "opentaint.go.env"
val goRootDir = layout.projectDirectory
val goServerDir = goRootDir.dir("go-ssa-server")
val goProtoDir = goRootDir.dir("proto")
val goServerBinary = goServerDir.file("go-ssa-server")
val goModulePath = "github.com/opentaint/go-ir/go-ssa-server"
val protocGenGoVersion = "v1.36.1"
val protocGenGoGrpcVersion = "v1.5.1"

fun goEnvironment(): Map<String, String> {
    return mapOf(
        "GOIR_SERVER_BINARY" to goServerBinary.asFile.absolutePath,
    )
}

// ─── Build tasks ────────────────────────────────────────────────────

tasks.register<Exec>("buildGoServer") {
    workingDir = goServerDir.asFile

    inputs.files(
        fileTree(goServerDir) {
            include("**/*.go")
            include("go.mod")
            include("go.sum")
        },
        fileTree(goProtoDir) {
            include("**/*.proto")
        }
    )
    outputs.file(goServerBinary)
    outputs.dir(goServerDir.dir("proto"))

    commandLine(
        "bash",
        "-lc",
        """
        set -euo pipefail
        GO_BIN_DIR="${'$'}PWD/.gradle-go-bin"
        export GOBIN="${'$'}GO_BIN_DIR"
        export PATH="${'$'}GO_BIN_DIR:${'$'}PATH"

        go install google.golang.org/protobuf/cmd/protoc-gen-go@$protocGenGoVersion
        go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@$protocGenGoGrpcVersion

        protoc \
          -I "$goProtoDir" \
          --go_out=. \
          --go_opt=module=$goModulePath \
          --go-grpc_out=. \
          --go-grpc_opt=module=$goModulePath \
          "$goProtoDir/goir/service.proto"

        go build -o "$goServerBinary" ./cmd/go-ssa-server
        """.trimIndent()
    )
}

// ─── Environment setup tasks ────────────────────────────────────────

tasks.register<DefaultTask>("setupGoEnvironment") {
    group = "go"
    description = "Builds the Go SSA server and initializes Go environment metadata."
    dependsOn("buildGoServer")
    doFirst {
        extra.set(goEnvironmentExtraKey, goEnvironment())
    }
}
