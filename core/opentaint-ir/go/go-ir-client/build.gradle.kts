import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("kotlin-conventions")
    id("com.google.protobuf") version "0.9.4"
}

dependencies {
    api(project(":go:go-ir-api"))
    implementation("io.grpc:grpc-netty-shaded:1.69.0")
    implementation("io.grpc:grpc-protobuf:1.69.0")
    implementation("io.grpc:grpc-stub:1.69.0")
    implementation("io.grpc:grpc-kotlin-stub:1.4.1")
    implementation("com.google.protobuf:protobuf-java:4.29.3")
    implementation("com.google.protobuf:protobuf-kotlin:4.29.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.1"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.60.0"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.1:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
                create("grpckt")
            }
            it.builtins {
                create("kotlin")
            }
        }
    }
}

sourceSets {
    main {
        proto {
            srcDir("${project.parent?.projectDir}/proto")
        }
    }
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.remove("-Werror")
}

val goRootDir = project.parent!!.projectDir
val goServerDir = goRootDir.resolve("go-ssa-server")
val goProtoDir = goRootDir.resolve("proto")
val goServerBinary = goServerDir.resolve("go-ssa-server")
val goModulePath = "github.com/opentaint/go-ir/go-ssa-server"
val protocGenGoVersion = "v1.36.1"
val protocGenGoGrpcVersion = "v1.5.1"

tasks.register<Exec>("buildGoServer") {
    workingDir = goServerDir

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
    outputs.dir(goServerDir.resolve("proto"))

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
