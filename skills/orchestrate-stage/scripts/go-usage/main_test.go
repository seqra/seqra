package main

import (
	"go/token"
	"go/types"
	"testing"
)

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
