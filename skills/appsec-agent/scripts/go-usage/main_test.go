package main

import (
	"go/token"
	"go/types"
	"os"
	"path/filepath"
	"reflect"
	"testing"
)

func TestExtractFunctionReferences(t *testing.T) {
	dir := t.TempDir()
	files := map[string]string{
		"go.mod": "module example.com/app\n\ngo 1.25.0\n",
		"lib/lib.go": `package lib
func Direct(string) {}
func Assigned(string) {}
func Callback(string) {}
func Generic[T any](T) {}
func Unused(string) {}
type Worker struct{}
func (*Worker) Bound(string) {}
func (Worker) Expression(string) {}
`,
		"app.go": `package app
import "example.com/app/lib"
var stored = lib.Callback
func use(w *lib.Worker) {
    lib.Direct("value")
    f := lib.Assigned
    f("value")
    g := lib.Generic[string]
    g("value")
    method := w.Bound
    method("value")
    expression := lib.Worker.Expression
    expression(*w, "value")
    stored("value")
}
`,
	}
	for name, content := range files {
		path := filepath.Join(dir, name)
		if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	rows, err := extract(dir)
	if err != nil {
		t.Fatal(err)
	}
	want := []usage{
		{Method: "(*example.com/app/lib.Worker).Bound", Signature: "args:1"},
		{Method: "(example.com/app/lib.Worker).Expression", Signature: "args:1"},
		{Method: "example.com/app/lib.Assigned", Signature: "args:1"},
		{Method: "example.com/app/lib.Callback", Signature: "args:1"},
		{Method: "example.com/app/lib.Direct", Signature: "args:1"},
		{Method: "example.com/app/lib.Generic", Signature: "args:1"},
	}
	if !reflect.DeepEqual(rows, want) {
		t.Fatalf("usages = %#v, want %#v", rows, want)
	}
}

func TestFunctionName(t *testing.T) {
	pkg := types.NewPackage("example.com/module/lib", "lib")
	params := types.NewTuple(types.NewParam(token.NoPos, pkg, "value", types.Typ[types.String]))
	results := types.NewTuple(types.NewParam(token.NoPos, pkg, "result", types.Typ[types.String]))

	plainSig := types.NewSignatureType(nil, nil, nil, params, results, false)
	plain := types.NewFunc(token.NoPos, pkg, "Transform", plainSig)
	if got := functionName(plain, plainSig); got != "example.com/module/lib.Transform" {
		t.Fatalf("package function name = %q", got)
	}

	typeName := types.NewTypeName(token.NoPos, pkg, "Worker", nil)
	named := types.NewNamed(typeName, types.NewStruct(nil, nil), nil)
	receiver := types.NewVar(token.NoPos, pkg, "worker", types.NewPointer(named))
	methodSig := types.NewSignatureType(receiver, nil, nil, params, results, false)
	method := types.NewFunc(token.NoPos, pkg, "Transform", methodSig)
	if got := functionName(method, methodSig); got != "(*example.com/module/lib.Worker).Transform" {
		t.Fatalf("method name = %q", got)
	}
}
