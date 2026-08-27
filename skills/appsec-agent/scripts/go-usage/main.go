package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"go/ast"
	"go/types"
	"os"
	"sort"

	"golang.org/x/tools/go/packages"
)

type usage struct {
	Method    string `json:"method"`
	Signature string `json:"signature"`
}

func main() {
	dir := flag.String("dir", "", "Go project directory")
	flag.Parse()
	if *dir == "" {
		fmt.Fprintln(os.Stderr, "--dir is required")
		os.Exit(2)
	}

	rows, err := extract(*dir)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	if err := json.NewEncoder(os.Stdout).Encode(rows); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func extract(dir string) ([]usage, error) {
	cfg := &packages.Config{
		Mode: packages.NeedName | packages.NeedFiles | packages.NeedCompiledGoFiles |
			packages.NeedImports | packages.NeedDeps | packages.NeedSyntax |
			packages.NeedTypes | packages.NeedTypesInfo | packages.NeedModule,
		Dir: dir,
	}
	loaded, err := packages.Load(cfg, "./...")
	if err != nil {
		return nil, fmt.Errorf("load Go packages: %w", err)
	}
	var loadErrors []error
	packages.Visit(loaded, nil, func(pkg *packages.Package) {
		for _, pkgErr := range pkg.Errors {
			loadErrors = append(loadErrors, pkgErr)
		}
	})
	if len(loadErrors) != 0 {
		return nil, fmt.Errorf("load Go packages: %v", loadErrors[0])
	}

	seen := make(map[usage]bool)
	for _, pkg := range loaded {
		for _, file := range pkg.Syntax {
			ast.Inspect(file, func(node ast.Node) bool {
				call, ok := node.(*ast.CallExpr)
				if !ok {
					return true
				}
				fn := calledFunction(call.Fun, pkg.TypesInfo)
				if fn == nil || fn.Pkg() == nil {
					return true
				}
				sig, ok := fn.Type().(*types.Signature)
				if !ok {
					return true
				}
				seen[usage{
					Method:    functionName(fn, sig),
					Signature: fmt.Sprintf("args:%d", sig.Params().Len()),
				}] = true
				return true
			})
		}
	}

	rows := make([]usage, 0, len(seen))
	for row := range seen {
		rows = append(rows, row)
	}
	sort.Slice(rows, func(i, j int) bool {
		if rows[i].Method != rows[j].Method {
			return rows[i].Method < rows[j].Method
		}
		return rows[i].Signature < rows[j].Signature
	})
	return rows, nil
}

func calledFunction(expr ast.Expr, info *types.Info) *types.Func {
	switch value := expr.(type) {
	case *ast.ParenExpr:
		return calledFunction(value.X, info)
	case *ast.IndexExpr:
		return calledFunction(value.X, info)
	case *ast.IndexListExpr:
		return calledFunction(value.X, info)
	case *ast.Ident:
		fn, _ := info.Uses[value].(*types.Func)
		return fn
	case *ast.SelectorExpr:
		if selection := info.Selections[value]; selection != nil {
			fn, _ := selection.Obj().(*types.Func)
			return fn
		}
		fn, _ := info.Uses[value.Sel].(*types.Func)
		return fn
	default:
		return nil
	}
}

func functionName(fn *types.Func, sig *types.Signature) string {
	pkgPath := fn.Pkg().Path()
	if sig.Recv() == nil {
		return pkgPath + "." + fn.Name()
	}

	receiver := sig.Recv().Type()
	pointer := false
	if ptr, ok := receiver.(*types.Pointer); ok {
		pointer = true
		receiver = ptr.Elem()
	}
	named, ok := receiver.(*types.Named)
	if !ok {
		return pkgPath + "." + fn.Name()
	}
	prefix := "("
	if pointer {
		prefix += "*"
	}
	return prefix + pkgPath + "." + named.Obj().Name() + ")." + fn.Name()
}
