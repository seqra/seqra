package server

import (
	"context"
	"io"
	"net"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/test/bufconn"

	pb "github.com/opentaint/go-ir/go-ssa-server/proto/goir"
)

type buildResult struct {
	program *pb.ProtoProgram
	models  []*pb.ProtoModelProgram
}

func writeTinyModule(t *testing.T) string {
	t.Helper()
	dir := t.TempDir()
	writeFile(t, filepath.Join(dir, "go.mod"), "module example.com/m\ngo 1.22\n")
	writeFile(
		t,
		filepath.Join(dir, "main.go"),
		"package main\nimport \"strings\"\nfunc Greet(s string) string { return strings.ToUpper(s) }\nfunc main() { println(Greet(\"x\")) }\n",
	)
	return dir
}

func writeStringsModel(t *testing.T, source string) string {
	t.Helper()
	dir := t.TempDir()
	writeFile(t, filepath.Join(dir, "go.mod"), "module opentaint\ngo 1.25\n")
	packageDir := filepath.Join(dir, "strings")
	if err := os.MkdirAll(packageDir, 0o755); err != nil {
		t.Fatal(err)
	}
	writeFile(t, filepath.Join(packageDir, "model.go"), source)
	return dir
}

func writeFile(t *testing.T, path, content string) {
	t.Helper()
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
}

func startBuild(t *testing.T, req *pb.BuildProgramRequest) pb.GoSSAService_BuildProgramClient {
	t.Helper()
	lis := bufconn.Listen(1 << 20)
	srv := grpc.NewServer()
	pb.RegisterGoSSAServiceServer(srv, NewGoSSAServer("test", "test"))
	go srv.Serve(lis)
	t.Cleanup(srv.Stop)

	conn, err := grpc.NewClient(
		"passthrough:///bufnet",
		grpc.WithContextDialer(func(ctx context.Context, _ string) (net.Conn, error) {
			return lis.DialContext(ctx)
		}),
		grpc.WithTransportCredentials(insecure.NewCredentials()),
		grpc.WithDefaultCallOptions(grpc.MaxCallRecvMsgSize(256*1024*1024)),
	)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = conn.Close() })

	stream, err := pb.NewGoSSAServiceClient(conn).BuildProgram(context.Background(), req)
	if err != nil {
		t.Fatal(err)
	}
	return stream
}

func runBuildResult(t *testing.T, req *pb.BuildProgramRequest) buildResult {
	t.Helper()
	stream := startBuild(t, req)
	result := buildResult{program: &pb.ProtoProgram{}}
	for {
		resp, err := stream.Recv()
		if err == io.EOF {
			return result
		}
		if err != nil {
			t.Fatalf("stream error: %v", err)
		}
		switch payload := resp.Payload.(type) {
		case *pb.BuildProgramResponse_Program:
			result.program = payload.Program
		case *pb.BuildProgramResponse_ModelProgram:
			result.models = append(result.models, payload.ModelProgram)
		case *pb.BuildProgramResponse_PackageDef:
			result.program.Packages = append(result.program.Packages, payload.PackageDef)
		case *pb.BuildProgramResponse_FunctionBody:
			result.program.FunctionBodies = append(result.program.FunctionBodies, payload.FunctionBody)
		case *pb.BuildProgramResponse_Error:
			if payload.Error.Fatal {
				t.Fatalf("fatal: %s", payload.Error.Message)
			}
		}
	}
}

func runBuildFatal(t *testing.T, req *pb.BuildProgramRequest) string {
	t.Helper()
	stream := startBuild(t, req)
	for {
		resp, err := stream.Recv()
		if err == io.EOF {
			t.Fatal("expected a fatal Go model build error")
		}
		if err != nil {
			t.Fatalf("expected a Go model build error, got stream error: %v", err)
		}
		if failure, ok := resp.Payload.(*pb.BuildProgramResponse_Error); ok && failure.Error.Fatal {
			return failure.Error.Message
		}
	}
}

func findPkg(pkgs []*pb.ProtoPackage, path string) *pb.ProtoPackage {
	for _, pkg := range pkgs {
		if pkg.ImportPath == path {
			return pkg
		}
	}
	return nil
}

func bodyIDsOf(pkg *pb.ProtoPackage, bodies map[int32]bool) (withBody, total int) {
	for _, function := range pkg.Functions {
		if function.HasBody {
			total++
			if bodies[function.Id] {
				withBody++
			}
		}
	}
	return
}

func TestBuildProgram_ProjectMode_NoStdlibBodies(t *testing.T) {
	result := runBuildResult(t, &pb.BuildProgramRequest{
		WorkingDir: writeTinyModule(t), Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT,
		InstantiateGenerics: true, SanityCheck: true,
	})
	bodies := make(map[int32]bool, len(result.program.FunctionBodies))
	for _, body := range result.program.FunctionBodies {
		bodies[body.FunctionId] = true
	}

	mainPackage := findPkg(result.program.Packages, "example.com/m")
	if mainPackage == nil || mainPackage.IsDependency || mainPackage.IsStdlib {
		t.Fatalf("main package missing or misclassified: %+v", mainPackage)
	}
	withBody, total := bodyIDsOf(mainPackage, bodies)
	if total == 0 || withBody != total {
		t.Fatalf("project bodies: want all %d, got %d", total, withBody)
	}
	stringsPackage := findPkg(result.program.Packages, "strings")
	if stringsPackage == nil || !stringsPackage.IsStdlib {
		t.Fatalf("strings not present or not standard library: %+v", stringsPackage)
	}
	if withBody, _ := bodyIDsOf(stringsPackage, bodies); withBody != 0 {
		t.Fatalf("PROJECT mode streamed %d standard library bodies, want 0", withBody)
	}
}

func TestBuildProgram_FullMode_StreamsStdlibBodies(t *testing.T) {
	result := runBuildResult(t, &pb.BuildProgramRequest{
		WorkingDir: writeTinyModule(t), Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_FULL,
		InstantiateGenerics: true, SanityCheck: true,
	})
	bodies := make(map[int32]bool, len(result.program.FunctionBodies))
	for _, body := range result.program.FunctionBodies {
		bodies[body.FunctionId] = true
	}
	stringsPackage := findPkg(result.program.Packages, "strings")
	if stringsPackage == nil || !stringsPackage.IsStdlib {
		t.Fatalf("strings package is missing or not standard library: %+v", stringsPackage)
	}
	if withBody, _ := bodyIDsOf(stringsPackage, bodies); withBody == 0 {
		t.Fatal("FULL mode streamed no standard library bodies")
	}
}

func TestBuildProgram_StreamsRawGoModelForKotlinResolution(t *testing.T) {
	modelDir := writeStringsModel(
		t,
		"package strings\nfunc ToUpper(value string) string { return value }\n",
	)
	result := runBuildResult(t, &pb.BuildProgramRequest{
		WorkingDir: writeTinyModule(t), Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT,
		InstantiateGenerics: true, SanityCheck: true, ModelDirs: []string{modelDir},
	})

	if len(result.models) != 1 {
		t.Fatalf("want one raw model program, got %d", len(result.models))
	}
	model := result.models[0]
	if model.Source != modelDir {
		t.Fatalf("model source is %q, want %q", model.Source, modelDir)
	}
	if findPkg(model.Program.Packages, "opentaint/strings") == nil {
		t.Fatal("raw model program has no opentaint/strings package")
	}
	if findPkg(result.program.Packages, "opentaint/strings") != nil {
		t.Fatal("base program contains the raw model package")
	}
	stringsPackage := findPkg(result.program.Packages, "strings")
	if stringsPackage == nil {
		t.Fatal("base program has no strings package")
	}
	var toUpperID int32
	for _, function := range stringsPackage.Functions {
		if function.Name == "ToUpper" {
			toUpperID = function.Id
		}
	}
	for _, body := range result.program.FunctionBodies {
		if body.FunctionId == toUpperID {
			t.Fatal("Go server attached the model body to the base program")
		}
	}
}

func TestBuildProgram_GoModelMustCompile(t *testing.T) {
	modelDir := writeStringsModel(
		t,
		"package strings\nfunc ToUpper(value string) string { return missing }\n",
	)
	message := runBuildFatal(t, &pb.BuildProgramRequest{
		WorkingDir: writeTinyModule(t), Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT,
		InstantiateGenerics: true, SanityCheck: true, ModelDirs: []string{modelDir},
	})
	if !strings.Contains(message, "Go model does not compile") || !strings.Contains(message, "undefined: missing") {
		t.Fatalf("unexpected compilation error: %s", message)
	}
}

func TestBuildProgram_GoModelCanImportTargetProjectModule(t *testing.T) {
	projectDir := t.TempDir()
	writeFile(t, filepath.Join(projectDir, "go.mod"), "module example.com/dependency\ngo 1.22\n")
	writeFile(
		t,
		filepath.Join(projectDir, "dependency.go"),
		"package dependency\ntype Value struct { Text string }\nfunc Identity(value Value) Value { return Value{} }\n",
	)

	modelDir := t.TempDir()
	writeFile(t, filepath.Join(modelDir, "go.mod"), "module opentaint\ngo 1.25\n")
	modelPackageDir := filepath.Join(modelDir, "example.com", "dependency")
	if err := os.MkdirAll(modelPackageDir, 0o755); err != nil {
		t.Fatal(err)
	}
	writeFile(
		t,
		filepath.Join(modelPackageDir, "model.go"),
		"package dependency\nimport target \"example.com/dependency\"\nfunc Identity(value target.Value) target.Value { return value }\n",
	)

	result := runBuildResult(t, &pb.BuildProgramRequest{
		WorkingDir: projectDir, Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT,
		InstantiateGenerics: true, SanityCheck: true, ModelDirs: []string{modelDir},
	})
	if len(result.models) != 1 {
		t.Fatalf("want one raw model program, got %d", len(result.models))
	}
	if findPkg(result.models[0].Program.Packages, "example.com/dependency") == nil {
		t.Fatal("raw model program has no imported target project package")
	}
}

func TestBuildProgram_GoModelCanImportResolvedProjectDependency(t *testing.T) {
	dependencyDir := t.TempDir()
	writeFile(t, filepath.Join(dependencyDir, "go.mod"), "module example.com/library\ngo 1.22\n")
	writeFile(
		t,
		filepath.Join(dependencyDir, "library.go"),
		"package library\ntype Value struct { Text string }\nfunc Identity(value Value) Value { return Value{} }\n",
	)

	projectDir := t.TempDir()
	projectGoMod := "module example.com/app\ngo 1.22\nrequire example.com/library v0.0.0\n" +
		"replace example.com/library => " + dependencyDir + "\n"
	writeFile(t, filepath.Join(projectDir, "go.mod"), projectGoMod)
	writeFile(
		t,
		filepath.Join(projectDir, "main.go"),
		"package main\nimport \"example.com/library\"\nfunc Use(value library.Value) library.Value { return library.Identity(value) }\n",
	)

	modelDir := t.TempDir()
	writeFile(t, filepath.Join(modelDir, "go.mod"), "module opentaint\ngo 1.25\n")
	modelPackageDir := filepath.Join(modelDir, "example.com", "library")
	if err := os.MkdirAll(modelPackageDir, 0o755); err != nil {
		t.Fatal(err)
	}
	writeFile(
		t,
		filepath.Join(modelPackageDir, "model.go"),
		"package library\nimport target \"example.com/library\"\nfunc Identity(value target.Value) target.Value { return value }\n",
	)

	result := runBuildResult(t, &pb.BuildProgramRequest{
		WorkingDir: projectDir, Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT,
		InstantiateGenerics: true, SanityCheck: true, ModelDirs: []string{modelDir},
	})
	if len(result.models) != 1 {
		t.Fatalf("want one raw model program, got %d", len(result.models))
	}
	dependency := findPkg(result.models[0].Program.Packages, "example.com/library")
	if dependency == nil || !dependency.IsDependency {
		t.Fatalf("raw model has no resolved project dependency: %+v", dependency)
	}
}
