package server

import (
	"fmt"
	"log"
	"os"

	"golang.org/x/tools/go/packages"
	"golang.org/x/tools/go/ssa"
	"golang.org/x/tools/go/ssa/ssautil"

	pb "github.com/opentaint/go-ir/go-ssa-server/proto/goir"
)

func buildSSA(req *pb.BuildProgramRequest) (*ssa.Program, []*ssa.Package, error) {
	cfg := &packages.Config{
		Mode: packages.NeedFiles | packages.NeedSyntax | packages.NeedTypes |
			packages.NeedTypesInfo | packages.NeedImports | packages.NeedDeps |
			packages.NeedName | packages.NeedModule,
		Dir: req.WorkingDir,
	}

	if len(req.BuildTags) > 0 {
		cfg.BuildFlags = []string{"-tags=" + joinTags(req.BuildTags)}
	}

	if req.Gopath != "" {
		env := os.Environ()
		env = append(env, "GOPATH="+req.Gopath)
		cfg.Env = env
	}
	if req.Goroot != "" {
		if cfg.Env == nil {
			cfg.Env = os.Environ()
		}
		cfg.Env = append(cfg.Env, "GOROOT="+req.Goroot)
	}

	// Load packages
	pkgs, err := packages.Load(cfg, req.Patterns...)
	if err != nil {
		return nil, nil, fmt.Errorf("packages.Load: %w", err)
	}

	// Check for load errors (non-fatal — log and continue)
	for _, pkg := range pkgs {
		for _, e := range pkg.Errors {
			log.Printf("WARN: package %s: %v", pkg.PkgPath, e)
		}
	}

	// Build SSA
	mode := ssa.BuilderMode(0)
	if req.InstantiateGenerics {
		mode |= ssa.InstantiateGenerics
	}
	if req.SanityCheck {
		mode |= ssa.SanityCheckFunctions
	}

	prog, ssaPkgs := ssautil.AllPackages(pkgs, mode)
	prog.Build()

	// Filter out nil packages (can happen for packages with errors)
	var validPkgs []*ssa.Package
	for _, p := range ssaPkgs {
		if p != nil {
			validPkgs = append(validPkgs, p)
		}
	}

	return prog, validPkgs, nil
}

func joinTags(tags []string) string {
	result := ""
	for i, t := range tags {
		if i > 0 {
			result += ","
		}
		result += t
	}
	return result
}
