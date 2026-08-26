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

func writeTinyModule(t *testing.T) string {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "go.mod"), []byte("module example.com/m\ngo 1.22\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	src := "package main\nimport \"strings\"\nfunc Greet(s string) string { return strings.ToUpper(s) }\nfunc main() { println(Greet(\"x\")) }\n"
	if err := os.WriteFile(filepath.Join(dir, "main.go"), []byte(src), 0o644); err != nil {
		t.Fatal(err)
	}
	return dir
}

func runBuildProgram(t *testing.T, req *pb.BuildProgramRequest) *pb.ProtoProgram {
	lis := bufconn.Listen(1 << 20)
	srv := grpc.NewServer()
	pb.RegisterGoSSAServiceServer(srv, NewGoSSAServer("test", "test"))
	go srv.Serve(lis)
	defer srv.Stop()

	conn, err := grpc.NewClient("passthrough:///bufnet",
		grpc.WithContextDialer(func(ctx context.Context, _ string) (net.Conn, error) { return lis.DialContext(ctx) }),
		grpc.WithTransportCredentials(insecure.NewCredentials()),
		// The bulk program payload can exceed gRPC's default 4 MB receive limit;
		// mirror the production client (GoSsaServerProcess) which allows 256 MB.
		grpc.WithDefaultCallOptions(grpc.MaxCallRecvMsgSize(256*1024*1024)))
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()

	stream, err := pb.NewGoSSAServiceClient(conn).BuildProgram(context.Background(), req)
	if err != nil {
		t.Fatal(err)
	}
	program := &pb.ProtoProgram{}
	for {
		resp, err := stream.Recv()
		if err != nil {
			if err != io.EOF {
				t.Fatalf("stream error: %v", err)
			}
			break
		}
		switch p := resp.Payload.(type) {
		case *pb.BuildProgramResponse_Program:
			program = p.Program
		case *pb.BuildProgramResponse_PackageDef:
			program.Packages = append(program.Packages, p.PackageDef)
		case *pb.BuildProgramResponse_FunctionBody:
			program.FunctionBodies = append(program.FunctionBodies, p.FunctionBody)
		case *pb.BuildProgramResponse_Error:
			if p.Error.Fatal {
				t.Fatalf("fatal: %s", p.Error.Message)
			}
		}
	}
	return program
}

func runBuild(t *testing.T, req *pb.BuildProgramRequest) ([]*pb.ProtoPackage, map[int32]bool) {
	program := runBuildProgram(t, req)
	bodies := make(map[int32]bool, len(program.FunctionBodies))
	for _, body := range program.FunctionBodies {
		bodies[body.FunctionId] = true
	}
	return program.Packages, bodies
}

func runBuildFatal(t *testing.T, req *pb.BuildProgramRequest) string {
	t.Helper()
	lis := bufconn.Listen(1 << 20)
	srv := grpc.NewServer()
	pb.RegisterGoSSAServiceServer(srv, NewGoSSAServer("test", "test"))
	go srv.Serve(lis)
	defer srv.Stop()
	conn, err := grpc.NewClient("passthrough:///bufnet",
		grpc.WithContextDialer(func(ctx context.Context, _ string) (net.Conn, error) { return lis.DialContext(ctx) }),
		grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	stream, err := pb.NewGoSSAServiceClient(conn).BuildProgram(context.Background(), req)
	if err != nil {
		t.Fatal(err)
	}
	for {
		resp, recvErr := stream.Recv()
		if recvErr == io.EOF {
			t.Fatal("expected a fatal model validation error")
		}
		if recvErr != nil {
			t.Fatalf("expected a model validation error, got stream error: %v", recvErr)
		}
		if failure, ok := resp.Payload.(*pb.BuildProgramResponse_Error); ok && failure.Error.Fatal {
			return failure.Error.Message
		}
	}
}

func findPkg(pkgs []*pb.ProtoPackage, path string) *pb.ProtoPackage {
	for _, p := range pkgs {
		if p.ImportPath == path {
			return p
		}
	}
	return nil
}

func bodyIDsOf(p *pb.ProtoPackage, bodies map[int32]bool) (withBody, total int) {
	for _, f := range p.Functions {
		if f.HasBody {
			total++
			if bodies[f.Id] {
				withBody++
			}
		}
	}
	return
}

func TestBuildProgram_ProjectMode_NoStdlibBodies(t *testing.T) {
	dir := writeTinyModule(t)
	pkgs, bodies := runBuild(t, &pb.BuildProgramRequest{WorkingDir: dir, Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT, InstantiateGenerics: true, SanityCheck: true})

	main := findPkg(pkgs, "example.com/m")
	if main == nil || main.IsDependency || main.IsStdlib {
		t.Fatalf("main package missing or misclassified: %+v", main)
	}
	wb, total := bodyIDsOf(main, bodies)
	if total == 0 || wb != total {
		t.Fatalf("project bodies: want all %d, got %d", total, wb)
	}
	strs := findPkg(pkgs, "strings")
	if strs == nil || !strs.IsStdlib {
		t.Fatalf("strings not present/stdlib: %+v", strs)
	}
	if wb, _ := bodyIDsOf(strs, bodies); wb != 0 {
		t.Fatalf("PROJECT mode streamed %d stdlib bodies, want 0", wb)
	}
}

func TestBuildProgram_FullMode_StreamsStdlibBodies(t *testing.T) {
	dir := writeTinyModule(t)
	pkgs, bodies := runBuild(t, &pb.BuildProgramRequest{WorkingDir: dir, Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_FULL, InstantiateGenerics: true, SanityCheck: true})
	strs := findPkg(pkgs, "strings")
	if strs == nil {
		t.Fatal("strings package missing in FULL mode")
	}
	if !strs.IsStdlib {
		t.Fatalf("strings not stdlib: %+v", strs)
	}
	if wb, _ := bodyIDsOf(strs, bodies); wb == 0 {
		t.Fatal("FULL mode streamed no stdlib bodies")
	}
}

func writeStringsModel(t *testing.T, packageName string) string {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "go.mod"), []byte("module opentaint\ngo 1.25\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	packageDir := filepath.Join(dir, "strings")
	if err := os.MkdirAll(packageDir, 0o755); err != nil {
		t.Fatal(err)
	}
	src := "package " + packageName + "\nfunc ToUpper(s string) string { return s }\n"
	if err := os.WriteFile(filepath.Join(packageDir, "model.go"), []byte(src), 0o644); err != nil {
		t.Fatal(err)
	}
	return dir
}

func TestBuildProgram_GoModelReplacesTargetBody(t *testing.T) {
	dir := writeTinyModule(t)
	modelDir := writeStringsModel(t, "strings")
	program := runBuildProgram(t, &pb.BuildProgramRequest{
		WorkingDir: dir, Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT,
		InstantiateGenerics: true, SanityCheck: true, ModelDirs: []string{modelDir},
	})

	if findPkg(program.Packages, "opentaint/strings") != nil {
		t.Fatal("model-addressed package leaked into the merged program")
	}
	stringsPkg := findPkg(program.Packages, "strings")
	if stringsPkg == nil {
		t.Fatal("strings package missing")
	}
	var target *pb.ProtoFunction
	for _, fn := range stringsPkg.Functions {
		if fn.Name == "ToUpper" {
			target = fn
			break
		}
	}
	if target == nil {
		t.Fatal("strings.ToUpper missing")
	}
	var body *pb.ProtoFunctionBody
	for _, candidate := range program.FunctionBodies {
		if candidate.FunctionId == target.Id {
			body = candidate
			break
		}
	}
	if body == nil {
		t.Fatal("model body was not attached to strings.ToUpper")
	}
	if len(body.Blocks) != 1 || len(body.Blocks[0].Instructions) == 0 {
		t.Fatalf("unexpected model body: %+v", body)
	}
	ret := body.Blocks[0].Instructions[len(body.Blocks[0].Instructions)-1].GetReturnInst()
	if ret == nil || len(ret.Results) != 1 || ret.Results[0].GetParamIndex() != 0 {
		t.Fatalf("strings.ToUpper model does not return its parameter: %+v", ret)
	}
}

func TestBuildProgram_GoModelPackageNameMustMatchTarget(t *testing.T) {
	dir := writeTinyModule(t)
	modelDir := writeStringsModel(t, "modelstrings")
	message := runBuildFatal(t, &pb.BuildProgramRequest{
		WorkingDir: dir, Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT,
		InstantiateGenerics: true, SanityCheck: true, ModelDirs: []string{modelDir},
	})
	if !strings.Contains(message, "package name \"modelstrings\", want \"strings\"") {
		t.Fatalf("unexpected validation error: %s", message)
	}
}

func TestBuildProgram_GoModelFunctionSignatureMustMatchTarget(t *testing.T) {
	dir := writeTinyModule(t)
	modelDir := writeStringsModel(t, "strings")
	modelSource := "package strings\nfunc ToUpper(value int) int { return value }\n"
	if err := os.WriteFile(filepath.Join(modelDir, "strings", "model.go"), []byte(modelSource), 0o644); err != nil {
		t.Fatal(err)
	}

	message := runBuildFatal(t, &pb.BuildProgramRequest{
		WorkingDir: dir, Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT,
		InstantiateGenerics: true, SanityCheck: true, ModelDirs: []string{modelDir},
	})
	if !strings.Contains(message, "strings.ToUpper has a different signature") {
		t.Fatalf("unexpected validation error: %s", message)
	}
}

func TestBuildProgram_GoPackageCannotBeModeledTwice(t *testing.T) {
	dir := writeTinyModule(t)
	firstModel := writeStringsModel(t, "strings")
	secondModel := writeStringsModel(t, "strings")

	message := runBuildFatal(t, &pb.BuildProgramRequest{
		WorkingDir: dir, Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT,
		InstantiateGenerics: true, SanityCheck: true, ModelDirs: []string{firstModel, secondModel},
	})
	if !strings.Contains(message, "Go package \"strings\" is modeled more than once") {
		t.Fatalf("unexpected validation error: %s", message)
	}
}

func TestBuildProgram_GoModelDoesNotReplacePackageInitializer(t *testing.T) {
	projectDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(projectDir, "go.mod"), []byte("module example.com/initmodel\ngo 1.22\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	projectSource := "package initmodel\nvar Value string\nfunc init() { Value = \"original\" }\n"
	if err := os.WriteFile(filepath.Join(projectDir, "init.go"), []byte(projectSource), 0o644); err != nil {
		t.Fatal(err)
	}

	modelDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(modelDir, "go.mod"), []byte("module opentaint\ngo 1.25\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	modelPackageDir := filepath.Join(modelDir, "example.com", "initmodel")
	if err := os.MkdirAll(modelPackageDir, 0o755); err != nil {
		t.Fatal(err)
	}
	modelSource := "package initmodel\nvar Value string\nfunc init() { Value = \"model\" }\n"
	if err := os.WriteFile(filepath.Join(modelPackageDir, "model.go"), []byte(modelSource), 0o644); err != nil {
		t.Fatal(err)
	}

	program := runBuildProgram(t, &pb.BuildProgramRequest{
		WorkingDir: projectDir, Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT,
		InstantiateGenerics: true, SanityCheck: true, ModelDirs: []string{modelDir},
	})
	targetPackage := findPkg(program.Packages, "example.com/initmodel")
	if targetPackage == nil || targetPackage.InitFunctionId == 0 {
		t.Fatalf("target package initializer missing: %+v", targetPackage)
	}
	var sourceInitializerID int32
	for _, function := range targetPackage.Functions {
		if function.Name == "init#1" {
			sourceInitializerID = function.Id
			break
		}
	}
	if sourceInitializerID == 0 {
		t.Fatalf("target source initializer missing: %+v", targetPackage)
	}
	for _, body := range program.FunctionBodies {
		if body.FunctionId != sourceInitializerID {
			continue
		}
		var sawOriginal, sawModel bool
		for _, block := range body.Blocks {
			for _, instruction := range block.Instructions {
				store := instruction.GetStore()
				if store == nil || store.Val.GetConstVal() == nil {
					continue
				}
				sawOriginal = sawOriginal || store.Val.GetConstVal().GetStringValue() == "original"
				sawModel = sawModel || store.Val.GetConstVal().GetStringValue() == "model"
			}
		}
		if !sawOriginal || sawModel {
			t.Fatalf("package initializer was replaced by model: original=%t model=%t", sawOriginal, sawModel)
		}
		return
	}
	t.Fatal("target package initializer body missing")
}

func TestBuildProgram_GoModelPackageRequiresOpentaintPrefix(t *testing.T) {
	dir := writeTinyModule(t)
	modelDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(modelDir, "go.mod"), []byte("module models\ngo 1.25\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	packageDir := filepath.Join(modelDir, "strings")
	if err := os.MkdirAll(packageDir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(packageDir, "model.go"), []byte("package strings\nfunc ToUpper(value string) string { return value }\n"), 0o644); err != nil {
		t.Fatal(err)
	}

	message := runBuildFatal(t, &pb.BuildProgramRequest{
		WorkingDir: dir, Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT,
		InstantiateGenerics: true, SanityCheck: true, ModelDirs: []string{modelDir},
	})
	if !strings.Contains(message, "import path must be opentaint/<target-import-path>") {
		t.Fatalf("unexpected validation error: %s", message)
	}
}

func TestBuildProgram_GoModelMustCompile(t *testing.T) {
	dir := writeTinyModule(t)
	modelDir := writeStringsModel(t, "strings")
	if err := os.WriteFile(filepath.Join(modelDir, "strings", "model.go"), []byte("package strings\nfunc ToUpper(value string) string { return missing }\n"), 0o644); err != nil {
		t.Fatal(err)
	}

	message := runBuildFatal(t, &pb.BuildProgramRequest{
		WorkingDir: dir, Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT,
		InstantiateGenerics: true, SanityCheck: true, ModelDirs: []string{modelDir},
	})
	if !strings.Contains(message, "Go model does not compile") || !strings.Contains(message, "undefined: missing") {
		t.Fatalf("unexpected compilation error: %s", message)
	}
}

func TestBuildProgram_GoModelReplacesReceiverMethodBody(t *testing.T) {
	projectDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(projectDir, "go.mod"), []byte("module example.com/m\ngo 1.22\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	projectPackageDir := filepath.Join(projectDir, "box")
	if err := os.MkdirAll(projectPackageDir, 0o755); err != nil {
		t.Fatal(err)
	}
	projectSource := "package box\ntype Box struct{}\nfunc (b *Box) Value(value string) string { return \"original\" }\n"
	if err := os.WriteFile(filepath.Join(projectPackageDir, "box.go"), []byte(projectSource), 0o644); err != nil {
		t.Fatal(err)
	}

	modelDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(modelDir, "go.mod"), []byte("module opentaint\ngo 1.25\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	modelPackageDir := filepath.Join(modelDir, "example.com", "m", "box")
	if err := os.MkdirAll(modelPackageDir, 0o755); err != nil {
		t.Fatal(err)
	}
	modelSource := "package box\ntype Box struct{}\nfunc (b *Box) Value(value string) string { return value }\n"
	if err := os.WriteFile(filepath.Join(modelPackageDir, "model.go"), []byte(modelSource), 0o644); err != nil {
		t.Fatal(err)
	}

	program := runBuildProgram(t, &pb.BuildProgramRequest{
		WorkingDir: projectDir, Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT,
		InstantiateGenerics: true, SanityCheck: true, ModelDirs: []string{modelDir},
	})
	targetPackage := findPkg(program.Packages, "example.com/m/box")
	if targetPackage == nil {
		t.Fatal("modeled package missing")
	}
	var target *pb.ProtoFunction
	for _, fn := range targetPackage.Functions {
		if fn.Name == "Value" {
			target = fn
			break
		}
	}
	if target == nil || !target.IsMethod || !target.IsPointerReceiver {
		t.Fatalf("modeled receiver method missing: %+v", target)
	}
	for _, body := range program.FunctionBodies {
		if body.FunctionId != target.Id || len(body.Blocks) != 1 {
			continue
		}
		instructions := body.Blocks[0].Instructions
		ret := instructions[len(instructions)-1].GetReturnInst()
		if ret == nil || len(ret.Results) != 1 || ret.Results[0].GetParamIndex() != 1 {
			t.Fatalf("modeled method does not return its argument: %+v", ret)
		}
		return
	}
	t.Fatal("modeled receiver method body missing")
}

func TestBuildProgram_GoModelCanBePartialAndAddFieldsAndMethods(t *testing.T) {
	projectDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(projectDir, "go.mod"), []byte("module example.com/fields\ngo 1.22\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	projectSource := `package fields

type Box struct {
	Original string
	Count int
}

func (b *Box) Put(value string) { b.Original = "original" }
func (b *Box) Get() string { return b.Original }
func (b *Box) Keep(value string) string { return "original" }
`
	if err := os.WriteFile(filepath.Join(projectDir, "fields.go"), []byte(projectSource), 0o644); err != nil {
		t.Fatal(err)
	}

	modelDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(modelDir, "go.mod"), []byte("module opentaint\ngo 1.25\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	modelPackageDir := filepath.Join(modelDir, "example.com", "fields")
	if err := os.MkdirAll(modelPackageDir, 0o755); err != nil {
		t.Fatal(err)
	}
	modelSource := `package fields

type Box struct {
	Shadow string
	Original string
}

func (b *Box) Put(value string) {
	b.Shadow = value
	b.Original = value
}
func (b *Box) Get() string { return b.Shadow }
func (b *Box) Helper(value string) string {
	b.Shadow = value
	return b.Shadow
}
`
	if err := os.WriteFile(filepath.Join(modelPackageDir, "model.go"), []byte(modelSource), 0o644); err != nil {
		t.Fatal(err)
	}

	program := runBuildProgram(t, &pb.BuildProgramRequest{
		WorkingDir: projectDir, Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT,
		InstantiateGenerics: true, SanityCheck: true, ModelDirs: []string{modelDir},
	})
	targetPackage := findPkg(program.Packages, "example.com/fields")
	if targetPackage == nil {
		t.Fatal("modeled package missing")
	}

	var box *pb.ProtoNamedType
	for _, named := range targetPackage.NamedTypes {
		if named.Name == "Box" {
			box = named
			break
		}
	}
	if box == nil {
		t.Fatal("modeled Box type missing")
	}
	if len(box.Fields) != 3 || box.Fields[0].Name != "Original" || box.Fields[0].Index != 0 ||
		box.Fields[1].Name != "Count" || box.Fields[1].Index != 1 ||
		box.Fields[2].Name != "Shadow" || box.Fields[2].Index != 2 {
		t.Fatalf("model field was not appended to Box: %+v", box.Fields)
	}
	var underlying *pb.ProtoStructType
	for _, typeDefinition := range program.Types {
		if typeDefinition.Id == box.UnderlyingTypeId {
			underlying = typeDefinition.GetStructType()
			break
		}
	}
	if underlying == nil || len(underlying.Fields) != 3 || underlying.Fields[2].Name != "Shadow" {
		t.Fatalf("Box underlying type does not contain the model field: %+v", underlying)
	}

	functions := make(map[string]*pb.ProtoFunction)
	for _, function := range targetPackage.Functions {
		if function.ParentFunctionId == 0 {
			functions[function.Name] = function
		}
	}
	for _, name := range []string{"Put", "Get", "Keep", "Helper"} {
		if functions[name] == nil {
			t.Fatalf("function %s is missing", name)
		}
	}
	if !functions["Helper"].IsSynthetic || functions["Helper"].SyntheticKind != "opentaint model support" {
		t.Fatalf("model-only method is not synthetic: %+v", functions["Helper"])
	}

	bodies := make(map[int32]*pb.ProtoFunctionBody, len(program.FunctionBodies))
	for _, body := range program.FunctionBodies {
		bodies[body.FunctionId] = body
	}
	for _, name := range []string{"Put", "Get", "Helper"} {
		body := bodies[functions[name].Id]
		if body == nil {
			t.Fatalf("function %s has no model body", name)
		}
		var sawShadow, sawOriginal bool
		for _, block := range body.Blocks {
			for _, instruction := range block.Instructions {
				if access := instruction.GetFieldAddr(); access != nil && access.FieldName == "Shadow" {
					if access.FieldIndex != 2 {
						t.Fatalf("function %s uses Shadow index %d, want 2", name, access.FieldIndex)
					}
					sawShadow = true
				}
				if access := instruction.GetFieldAddr(); access != nil && access.FieldName == "Original" {
					if access.FieldIndex != 0 {
						t.Fatalf("function %s uses Original index %d, want 0", name, access.FieldIndex)
					}
					sawOriginal = true
				}
				if access := instruction.GetField(); access != nil && access.FieldName == "Shadow" {
					if access.FieldIndex != 2 {
						t.Fatalf("function %s uses Shadow index %d, want 2", name, access.FieldIndex)
					}
					sawShadow = true
				}
			}
		}
		if !sawShadow {
			t.Fatalf("function %s does not use the model-only field", name)
		}
		if name == "Put" && !sawOriginal {
			t.Fatal("Put does not use the existing field")
		}
	}

	keepBody := bodies[functions["Keep"].Id]
	if keepBody == nil {
		t.Fatal("unmodeled Keep body is missing")
	}
	var keptOriginal bool
	for _, block := range keepBody.Blocks {
		for _, instruction := range block.Instructions {
			result := instruction.GetReturnInst()
			if result != nil && len(result.Results) == 1 &&
				result.Results[0].GetConstVal().GetStringValue() == "original" {
				keptOriginal = true
			}
		}
	}
	if !keptOriginal {
		t.Fatal("partial model changed the unmodeled Keep body")
	}
}

func TestBuildProgram_GoModelCanImportTargetProjectModule(t *testing.T) {
	projectDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(projectDir, "go.mod"), []byte("module example.com/dependency\ngo 1.22\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	projectSource := `package dependency

type Value struct { Text string }

func Identity(value Value) Value { return Value{Text: "original"} }
`
	if err := os.WriteFile(filepath.Join(projectDir, "dependency.go"), []byte(projectSource), 0o644); err != nil {
		t.Fatal(err)
	}

	modelDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(modelDir, "go.mod"), []byte("module opentaint\ngo 1.25\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	modelPackageDir := filepath.Join(modelDir, "example.com", "dependency")
	if err := os.MkdirAll(modelPackageDir, 0o755); err != nil {
		t.Fatal(err)
	}
	modelSource := `package dependency

import target "example.com/dependency"

func Identity(value target.Value) target.Value { return value }
`
	if err := os.WriteFile(filepath.Join(modelPackageDir, "model.go"), []byte(modelSource), 0o644); err != nil {
		t.Fatal(err)
	}

	program := runBuildProgram(t, &pb.BuildProgramRequest{
		WorkingDir: projectDir, Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT,
		InstantiateGenerics: true, SanityCheck: true, ModelDirs: []string{modelDir},
	})
	targetPackage := findPkg(program.Packages, "example.com/dependency")
	if targetPackage == nil {
		t.Fatal("target dependency package missing")
	}
	var identity *pb.ProtoFunction
	for _, function := range targetPackage.Functions {
		if function.Name == "Identity" && function.ParentFunctionId == 0 {
			identity = function
			break
		}
	}
	if identity == nil {
		t.Fatal("target Identity function missing")
	}
	for _, body := range program.FunctionBodies {
		if body.FunctionId != identity.Id {
			continue
		}
		instructions := body.Blocks[0].Instructions
		result := instructions[len(instructions)-1].GetReturnInst()
		if result == nil || len(result.Results) != 1 || result.Results[0].GetParamIndex() != 0 {
			t.Fatalf("target dependency model does not return its parameter: %+v", result)
		}
		return
	}
	t.Fatal("target dependency model body missing")
}

func TestBuildProgram_GoModelCanImportResolvedProjectDependency(t *testing.T) {
	dependencyDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dependencyDir, "go.mod"), []byte("module example.com/library\ngo 1.22\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	dependencySource := `package library

type Value struct { Text string }

func Identity(value Value) Value { return Value{Text: "original"} }
`
	if err := os.WriteFile(filepath.Join(dependencyDir, "library.go"), []byte(dependencySource), 0o644); err != nil {
		t.Fatal(err)
	}

	projectDir := t.TempDir()
	projectGoMod := "module example.com/app\ngo 1.22\nrequire example.com/library v0.0.0\nreplace example.com/library => " + dependencyDir + "\n"
	if err := os.WriteFile(filepath.Join(projectDir, "go.mod"), []byte(projectGoMod), 0o644); err != nil {
		t.Fatal(err)
	}
	projectSource := `package main

import "example.com/library"

func Use(value library.Value) library.Value { return library.Identity(value) }
`
	if err := os.WriteFile(filepath.Join(projectDir, "main.go"), []byte(projectSource), 0o644); err != nil {
		t.Fatal(err)
	}

	modelDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(modelDir, "go.mod"), []byte("module opentaint\ngo 1.25\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	modelPackageDir := filepath.Join(modelDir, "example.com", "library")
	if err := os.MkdirAll(modelPackageDir, 0o755); err != nil {
		t.Fatal(err)
	}
	modelSource := `package library

import target "example.com/library"

func Identity(value target.Value) target.Value { return value }
`
	if err := os.WriteFile(filepath.Join(modelPackageDir, "model.go"), []byte(modelSource), 0o644); err != nil {
		t.Fatal(err)
	}

	program := runBuildProgram(t, &pb.BuildProgramRequest{
		WorkingDir: projectDir, Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT,
		InstantiateGenerics: true, SanityCheck: true, ModelDirs: []string{modelDir},
	})
	targetPackage := findPkg(program.Packages, "example.com/library")
	if targetPackage == nil || !targetPackage.IsDependency {
		t.Fatalf("resolved project dependency is missing: %+v", targetPackage)
	}
	var identity *pb.ProtoFunction
	for _, function := range targetPackage.Functions {
		if function.Name == "Identity" && function.ParentFunctionId == 0 {
			identity = function
			break
		}
	}
	if identity == nil {
		t.Fatal("dependency Identity function missing")
	}
	for _, body := range program.FunctionBodies {
		if body.FunctionId != identity.Id {
			continue
		}
		instructions := body.Blocks[0].Instructions
		result := instructions[len(instructions)-1].GetReturnInst()
		if result == nil || len(result.Results) != 1 || result.Results[0].GetParamIndex() != 0 {
			t.Fatalf("resolved dependency model does not return its parameter: %+v", result)
		}
		return
	}
	t.Fatal("resolved dependency model body missing")
}

func TestBuildProgram_GoModelExistingFieldTypeMustMatch(t *testing.T) {
	projectDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(projectDir, "go.mod"), []byte("module example.com/fieldtype\ngo 1.22\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	projectSource := "package fieldtype\ntype Box struct { Value string }\nfunc (b *Box) Put(value string) {}\n"
	if err := os.WriteFile(filepath.Join(projectDir, "fieldtype.go"), []byte(projectSource), 0o644); err != nil {
		t.Fatal(err)
	}

	modelDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(modelDir, "go.mod"), []byte("module opentaint\ngo 1.25\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	modelPackageDir := filepath.Join(modelDir, "example.com", "fieldtype")
	if err := os.MkdirAll(modelPackageDir, 0o755); err != nil {
		t.Fatal(err)
	}
	modelSource := "package fieldtype\ntype Box struct { Value int }\nfunc (b *Box) Put(value string) { b.Value = len(value) }\n"
	if err := os.WriteFile(filepath.Join(modelPackageDir, "model.go"), []byte(modelSource), 0o644); err != nil {
		t.Fatal(err)
	}

	message := runBuildFatal(t, &pb.BuildProgramRequest{
		WorkingDir: projectDir, Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT,
		InstantiateGenerics: true, SanityCheck: true, ModelDirs: []string{modelDir},
	})
	if !strings.Contains(message, "field example.com/fieldtype.Box.Value has a different type") {
		t.Fatalf("unexpected field validation error: %s", message)
	}
}

func TestBuildProgram_GoModelSupportsVersionedImportPathWithDifferentPackageName(t *testing.T) {
	projectDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(projectDir, "go.mod"), []byte("module github.com/acme/widget/v2\ngo 1.22\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(projectDir, "widget.go"), []byte("package widget\nfunc Identity(value string) string { return \"original\" }\n"), 0o644); err != nil {
		t.Fatal(err)
	}

	modelDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(modelDir, "go.mod"), []byte("module opentaint\ngo 1.25\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	modelPackageDir := filepath.Join(modelDir, "github.com", "acme", "widget", "v2")
	if err := os.MkdirAll(modelPackageDir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(modelPackageDir, "model.go"), []byte("package widget\nfunc Identity(value string) string { return value }\n"), 0o644); err != nil {
		t.Fatal(err)
	}

	program := runBuildProgram(t, &pb.BuildProgramRequest{
		WorkingDir: projectDir, Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT,
		InstantiateGenerics: true, SanityCheck: true, ModelDirs: []string{modelDir},
	})
	targetPackage := findPkg(program.Packages, "github.com/acme/widget/v2")
	if targetPackage == nil || targetPackage.Name != "widget" {
		t.Fatalf("versioned modeled package missing: %+v", targetPackage)
	}
	target := targetPackage.Functions[0]
	for _, body := range program.FunctionBodies {
		if body.FunctionId != target.Id {
			continue
		}
		ret := body.Blocks[0].Instructions[len(body.Blocks[0].Instructions)-1].GetReturnInst()
		if ret == nil || len(ret.Results) != 1 || ret.Results[0].GetParamIndex() != 0 {
			t.Fatalf("versioned package model body not applied: %+v", ret)
		}
		return
	}
	t.Fatal("versioned package model body missing")
}

func TestBuildProgram_GoModelAddsSyntheticHelpersUsedByModelBody(t *testing.T) {
	dir := writeTinyModule(t)
	modelDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(modelDir, "go.mod"), []byte("module opentaint\ngo 1.25\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	modelPackageDir := filepath.Join(modelDir, "strings")
	if err := os.MkdirAll(modelPackageDir, 0o755); err != nil {
		t.Fatal(err)
	}
	modelSource := "package strings\nfunc identity(value string) string { return value }\nfunc ToUpper(value string) string { return identity(value) }\n"
	if err := os.WriteFile(filepath.Join(modelPackageDir, "model.go"), []byte(modelSource), 0o644); err != nil {
		t.Fatal(err)
	}

	program := runBuildProgram(t, &pb.BuildProgramRequest{
		WorkingDir: dir, Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT,
		InstantiateGenerics: true, SanityCheck: true, ModelDirs: []string{modelDir},
	})
	stringsPackage := findPkg(program.Packages, "strings")
	var target, helper *pb.ProtoFunction
	for _, fn := range stringsPackage.Functions {
		switch fn.Name {
		case "ToUpper":
			target = fn
		case "identity":
			helper = fn
		}
	}
	if target == nil || helper == nil {
		t.Fatalf("target or helper missing: target=%+v helper=%+v", target, helper)
	}
	if !helper.IsSynthetic || helper.SyntheticKind != "opentaint model support" {
		t.Fatalf("model helper is not synthetic: %+v", helper)
	}

	var targetCallsHelper, helperHasBody bool
	for _, body := range program.FunctionBodies {
		if body.FunctionId == helper.Id {
			helperHasBody = true
		}
		if body.FunctionId != target.Id {
			continue
		}
		for _, block := range body.Blocks {
			for _, instruction := range block.Instructions {
				call := instruction.GetCall()
				if call != nil && call.GetCall().GetFunction().GetFunctionId() == helper.Id {
					targetCallsHelper = true
				}
			}
		}
	}
	if !targetCallsHelper || !helperHasBody {
		t.Fatalf("model helper reference was not preserved: called=%t body=%t", targetCallsHelper, helperHasBody)
	}
}

func TestBuildProgram_GoModelOwnsItsAnonymousFunctions(t *testing.T) {
	projectDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(projectDir, "go.mod"), []byte("module example.com/closures\ngo 1.22\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	projectSource := "package closures\nfunc Apply(value string) string { nested := func() string { return \"original\" }; return nested() }\n"
	if err := os.WriteFile(filepath.Join(projectDir, "closures.go"), []byte(projectSource), 0o644); err != nil {
		t.Fatal(err)
	}

	modelDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(modelDir, "go.mod"), []byte("module opentaint\ngo 1.25\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	modelPackageDir := filepath.Join(modelDir, "example.com", "closures")
	if err := os.MkdirAll(modelPackageDir, 0o755); err != nil {
		t.Fatal(err)
	}
	modelSource := "package closures\nfunc Apply(value string) string { nested := func(input string) string { return input }; return nested(value) }\n"
	if err := os.WriteFile(filepath.Join(modelPackageDir, "model.go"), []byte(modelSource), 0o644); err != nil {
		t.Fatal(err)
	}

	program := runBuildProgram(t, &pb.BuildProgramRequest{
		WorkingDir: projectDir, Patterns: []string{"./..."}, Mode: pb.LoadMode_LOAD_MODE_PROJECT,
		InstantiateGenerics: true, SanityCheck: true, ModelDirs: []string{modelDir},
	})
	targetPackage := findPkg(program.Packages, "example.com/closures")
	var target *pb.ProtoFunction
	functions := functionsByID(targetPackage.Functions)
	for _, fn := range targetPackage.Functions {
		if fn.Name == "Apply" && fn.ParentFunctionId == 0 {
			target = fn
			break
		}
	}
	if target == nil || len(target.AnonFunctionIds) != 1 {
		t.Fatalf("modeled parent does not own its closure: %+v", target)
	}
	closure := functions[target.AnonFunctionIds[0]]
	if closure == nil || closure.ParentFunctionId != target.Id || len(closure.Params) != 1 || !closure.IsSynthetic {
		t.Fatalf("model closure metadata not preserved: %+v", closure)
	}
	for _, body := range program.FunctionBodies {
		if body.FunctionId == closure.Id {
			return
		}
	}
	t.Fatal("model closure body missing")
}
