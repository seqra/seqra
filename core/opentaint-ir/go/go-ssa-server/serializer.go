package server

import (
	"fmt"
	"go/constant"
	"go/token"
	"go/types"
	"log"
	"sort"
	"strings"
	"unicode/utf8"

	"golang.org/x/tools/go/packages"
	"golang.org/x/tools/go/ssa"
	"golang.org/x/tools/go/ssa/ssautil"

	pb "github.com/opentaint/go-ir/go-ssa-server/proto/goir"
)

// sanitizeUTF8 ensures a string is valid UTF-8 by replacing invalid bytes.
func sanitizeUTF8(s string) string {
	if utf8.ValidString(s) {
		return s
	}
	return strings.ToValidUTF8(s, "\uFFFD")
}

type serializerStats struct {
	packageCount     int
	functionCount    int
	typeCount        int
	instructionCount int
}

type serializer struct {
	prog *ssa.Program
	pkgs []*ssa.Package
	ids  *idAllocator

	// All types discovered during collection, in order of ID assignment
	allTypes []types.Type

	// All functions to serialize bodies for
	allFunctions []*ssa.Function

	// All globals to (re)stream definitions for in this call
	allGlobals []*ssa.Global

	// Per-call dedupe sets. Distinct from idAllocator.streamedTypes which is
	// per-session: a type already streamed in a previous call is still skipped
	// by the session set, but within one collect pass we use these to avoid
	// duplicating entries in the slice.
	collectedTypes     map[types.Type]bool
	collectedFunctions map[*ssa.Function]bool
	collectedGlobals   map[*ssa.Global]bool

	// Tracks which functions have already been serialized as ProtoFunction
	serializedFunctions map[*ssa.Function]bool

	// Tracks which named types already have a ProtoNamedType emitted.
	emittedNamedTypes map[*types.TypeName]bool

	// Maps for value IDs within a function
	funcValueIDs map[ssa.Value]int32

	stats serializerStats

	mode               pb.LoadMode
	projectModulePaths map[string]bool
	onlyListedModules  bool
	loadInfo           map[string]*packages.Package
}

func newSerializerWithIDs(prog *ssa.Program, pkgs []*ssa.Package, ids *idAllocator) *serializer {
	return &serializer{
		prog:                prog,
		pkgs:                pkgs,
		ids:                 ids,
		collectedTypes:      make(map[types.Type]bool),
		collectedFunctions:  make(map[*ssa.Function]bool),
		collectedGlobals:    make(map[*ssa.Global]bool),
		serializedFunctions: make(map[*ssa.Function]bool),
		emittedNamedTypes:   make(map[*types.TypeName]bool),
	}
}

func newSerializerForBuild(prog *ssa.Program, pkgs []*ssa.Package, info map[string]*packages.Package, mode pb.LoadMode, projectModulePaths map[string]bool) *serializer {
	s := newSerializerWithIDs(prog, pkgs, newIDAllocator())
	s.mode = mode
	s.projectModulePaths = projectModulePaths
	s.loadInfo = info
	s.collectAll()
	return s
}

func newSerializerForModelBuild(
	prog *ssa.Program,
	pkgs []*ssa.Package,
	info map[string]*packages.Package,
	projectModulePaths map[string]bool,
) *serializer {
	s := newSerializerWithIDs(prog, pkgs, newIDAllocator())
	s.mode = pb.LoadMode_LOAD_MODE_PROJECT
	s.projectModulePaths = projectModulePaths
	s.onlyListedModules = true
	s.loadInfo = info
	s.collectAll()
	return s
}

func (s *serializer) collectAll() {
	for _, pkg := range s.pkgs {
		s.ids.packageID(pkg)
	}
	for _, pkg := range s.pkgs {
		for _, mem := range sortedMembers(pkg) {
			switch m := mem.(type) {
			case *ssa.Function:
				s.collectFunctionSignature(m)
			case *ssa.Global:
				s.ids.globalID(m)
				s.collectType(m.Type())
			case *ssa.NamedConst:
				s.ids.constID(m)
				s.collectType(m.Type())
			}
		}
		for _, mem := range sortedMembers(pkg) {
			if t, ok := mem.(*ssa.Type); ok {
				named := t.Object().(*types.TypeName)
				s.ids.namedID(named)
				s.collectType(named.Type())
				s.collectType(named.Type().Underlying())
				mset := s.prog.MethodSets.MethodSet(named.Type())
				for i := 0; i < mset.Len(); i++ {
					if fn := s.prog.MethodValue(mset.At(i)); fn != nil {
						s.collectFunctionSignature(fn)
					}
				}
				pmset := s.prog.MethodSets.MethodSet(types.NewPointer(named.Type()))
				for i := 0; i < pmset.Len(); i++ {
					if fn := s.prog.MethodValue(pmset.At(i)); fn != nil {
						s.collectFunctionSignature(fn)
					}
				}
			}
		}
	}
	for fn := range ssautil.AllFunctions(s.prog) {
		s.collectFunctionSignature(fn)
	}
	for fn := range ssautil.AllFunctions(s.prog) {
		if s.shouldEmitBody(fn) {
			s.collectFunctionBody(fn)
		}
	}
}

func (s *serializer) shouldEmitBody(fn *ssa.Function) bool {
	if len(fn.Blocks) == 0 {
		return false
	}
	if s.mode == pb.LoadMode_LOAD_MODE_FULL {
		return true
	}
	dp := declaredPackage(fn)
	if dp == nil {
		return fn.Package() == nil
	}
	isStdlib, isDep := classifyPackage(s.loadInfo[dp.Pkg.Path()], s.projectModulePaths, s.onlyListedModules)
	return !isDep && !isStdlib
}

// serializeProgram serializes the whole program (types, packages and function
// bodies) into a single bulk message. Named-type declarations are emitted by
// serializePackage for top-level named members; types not referenced as
// package-level members (function-local types, universe types like `error`)
// are drained afterwards into the correct ProtoPackage, then the type table
// is drained so every referenced id has a matching ProtoTypeDefinition.
func (s *serializer) serializeProgram() (*pb.ProtoProgram, []*pb.ProtoError) {
	program := &pb.ProtoProgram{}
	var errs []*pb.ProtoError

	protoPkgBySSA := make(map[*ssa.Package]*pb.ProtoPackage, len(s.pkgs))
	for _, pkg := range s.pkgs {
		pp := s.serializePackage(pkg)
		isStdlib, isDep := classifyPackage(s.loadInfo[pkg.Pkg.Path()], s.projectModulePaths, s.onlyListedModules)
		pp.IsStdlib = isStdlib
		pp.IsDependency = isDep
		protoPkgBySSA[pkg] = pp
		program.Packages = append(program.Packages, pp)
		s.stats.packageCount++
	}

	for _, fn := range s.allFunctions {
		if len(fn.Blocks) == 0 {
			continue
		}
		body, err := s.serializeFunctionBody(fn)
		if err != nil {
			errs = append(errs, &pb.ProtoError{
				Message:      fmt.Sprintf("serializing %s: %v", fn.String(), err),
				FunctionName: fn.String(),
				Fatal:        false,
			})
			continue
		}
		program.FunctionBodies = append(program.FunctionBodies, body)
	}

	// Drain remaining types and named-type declarations together. Each pass
	// may allocate new ids for the other table (e.g. a NamedRef emitted for a
	// previously-unseen *types.Named, or a named-type body referencing more
	// types), so loop until both are drained. Universe-scope named types
	// (`error`, `comparable`) are routed to a synthetic package so the
	// deserializer always has a non-nil owner.
	var universePkg *pb.ProtoPackage
	getUniversePkg := func() *pb.ProtoPackage {
		if universePkg == nil {
			universePkg = &pb.ProtoPackage{
				Id:         s.ids.packageID(nil),
				ImportPath: universePackagePath,
				Name:       universePackagePath,
				IsStdlib:   true,
			}
			program.Packages = append(program.Packages, universePkg)
			s.stats.packageCount++
		}
		return universePkg
	}

	typeCursor := 0
	for {
		progress := false
		for typeCursor < len(s.allTypes) {
			t := s.allTypes[typeCursor]
			typeCursor++
			td := s.serializeType(t)
			if td == nil {
				continue
			}
			program.Types = append(program.Types, td)
			s.stats.typeCount++
			progress = true
		}
		for tn := range s.ids.snapshotNamedIDs() {
			if s.emittedNamedTypes[tn] {
				continue
			}
			pp := s.protoPackageForTypeName(tn, protoPkgBySSA, getUniversePkg)
			pp.NamedTypes = append(pp.NamedTypes, s.serializeNamedType(tn))
			progress = true
		}
		// Drain referenced-but-unemitted functions (synthetic wrappers,
		// stdlib/dep functions, generic instantiations, anonymous functions in
		// non-user packages). Their signatures need to be in some
		// ProtoPackage.Functions list so client refs (Parent/Anon/Call) resolve.
		for fn := range s.ids.snapshotFunctionIDs() {
			if s.serializedFunctions[fn] {
				continue
			}
			pp := s.protoPackageForFunction(fn, protoPkgBySSA, getUniversePkg)
			pp.Functions = append(pp.Functions, s.serializeFunction(fn))
			s.serializedFunctions[fn] = true
			progress = true
		}
		if !progress {
			break
		}
	}

	return program, errs
}

// universePackagePath is the synthetic import path used for universe-scope
// named types (e.g. `error`, `comparable`) that don't belong to any real
// Go package. The deserializer needs every named type to map to a package.
const universePackagePath = "<universe>"

// protoPackageForTypeName chooses which ProtoPackage should host the
// declaration of the given named type. Falls back to the universe package
// when the type has no owner or the owner's SSA package isn't part of the
// serialized set.
func (s *serializer) protoPackageForTypeName(
	tn *types.TypeName,
	protoPkgBySSA map[*ssa.Package]*pb.ProtoPackage,
	universe func() *pb.ProtoPackage,
) *pb.ProtoPackage {
	if tn.Pkg() == nil {
		return universe()
	}
	ssaPkg := lookupSSAPackageByTypesPackage(s.prog, tn.Pkg())
	if ssaPkg == nil {
		return universe()
	}
	if pp, ok := protoPkgBySSA[ssaPkg]; ok {
		return pp
	}
	return universe()
}

// isInValueMethodSet reports whether the underlying *types.Func of sel is
// already present in the given value-receiver method set. SSA synthesizes a
// pointer-receiver wrapper for every value-receiver method (so that `*T`
// implements the same interfaces as `T`); those wrappers share the same
// *types.Func as the user-declared method and must not be emitted as separate
// top-level functions.
func isInValueMethodSet(mset *types.MethodSet, sel *types.Selection) bool {
	for j := 0; j < mset.Len(); j++ {
		if mset.At(j).Obj() == sel.Obj() {
			return true
		}
	}
	return false
}

// protoPackageForFunction chooses which ProtoPackage should host the
// declaration of the given function. Mirrors protoPackageForTypeName but for
// *ssa.Function: prefers fn.Package(), then the declared package (covers
// synthetic wrappers and instantiated generics whose Package() is nil), and
// finally the universe package as a catch-all.
func (s *serializer) protoPackageForFunction(
	fn *ssa.Function,
	protoPkgBySSA map[*ssa.Package]*pb.ProtoPackage,
	universe func() *pb.ProtoPackage,
) *pb.ProtoPackage {
	ssaPkg := fn.Package()
	if ssaPkg == nil {
		ssaPkg = declaredPackage(fn)
	}
	if ssaPkg == nil {
		return universe()
	}
	if pp, ok := protoPkgBySSA[ssaPkg]; ok {
		return pp
	}
	return universe()
}

func (s *serializer) collectFunction(fn *ssa.Function) {
	if s.collectedFunctions[fn] {
		return // already collected in this call
	}
	s.collectedFunctions[fn] = true
	s.collectFunctionSignature(fn)
	s.collectFunctionBody(fn)

	// Collect anonymous functions
	for _, anon := range fn.AnonFuncs {
		s.collectFunction(anon)
	}
}

func (s *serializer) collectFunctionSignature(fn *ssa.Function) {
	s.ids.functionID(fn)
	s.collectType(fn.Signature)
	if typeParams := fn.TypeParams(); typeParams != nil {
		for i := 0; i < typeParams.Len(); i++ {
			s.collectType(typeParams.At(i))
		}
	}
	for _, typeArg := range fn.TypeArgs() {
		s.collectType(typeArg)
	}

	// Collect types from parameters
	for _, p := range fn.Params {
		s.collectType(p.Type())
	}
	for _, fv := range fn.FreeVars {
		s.collectType(fv.Type())
	}
}

func (s *serializer) collectFunctionBody(fn *ssa.Function) {
	s.collectFunctionSignature(fn)
	if len(fn.Blocks) == 0 {
		return
	}
	s.allFunctions = append(s.allFunctions, fn)
	// Collect types from instructions even when the function ID/signature was
	// assigned by a prior lazy package load. Body-local types are otherwise
	// missing from LoadFunctionBody streams and clients resolve them incorrectly.
	for _, block := range fn.Blocks {
		for _, inst := range block.Instrs {
			s.collectInstructionTypes(inst)
		}
	}
}

func (s *serializer) collectInstructionTypes(inst ssa.Instruction) {
	if v, ok := inst.(ssa.Value); ok {
		s.collectType(v.Type())
	}
	for _, op := range inst.Operands(nil) {
		if *op == nil {
			continue
		}
		s.collectType((*op).Type())
		switch v := (*op).(type) {
		case *ssa.Function:
			s.collectReferencedFunction(v)
		case *ssa.Global:
			s.collectReferencedGlobal(v)
		}
	}
	// Instruction-embedded types that aren't visible as operands.
	switch i := inst.(type) {
	case *ssa.TypeAssert:
		s.collectType(i.AssertedType)
	case *ssa.MakeInterface:
		s.collectType(i.X.Type())
		s.collectType(i.Type())
	case *ssa.ChangeType:
		s.collectType(i.X.Type())
		s.collectType(i.Type())
	case *ssa.ChangeInterface:
		s.collectType(i.X.Type())
		s.collectType(i.Type())
	case *ssa.Alloc:
		s.collectType(deref(i.Type()))
	}
}

func (s *serializer) collectReferencedFunction(fn *ssa.Function) {
	s.ids.functionID(fn)
	s.collectType(fn.Signature)
	for _, p := range fn.Params {
		s.collectType(p.Type())
	}
	for _, fv := range fn.FreeVars {
		s.collectType(fv.Type())
	}
	if s.collectedFunctions[fn] {
		return
	}
	if s.ids.isFunctionStreamed(fn) {
		return
	}
	s.collectedFunctions[fn] = true
}

func (s *serializer) collectReferencedGlobal(g *ssa.Global) {
	s.ids.globalID(g)
	s.collectType(g.Type())
	if s.collectedGlobals[g] {
		return
	}
	if s.ids.isGlobalStreamed(g) {
		return
	}
	s.collectedGlobals[g] = true
	s.allGlobals = append(s.allGlobals, g)
}

func (s *serializer) typeID(t types.Type) int32 {
	if t == nil {
		return 0
	}
	// Resolve type aliases to their target. Since Go 1.23 (gotypesalias=1 by
	// default) the type checker materializes *types.Alias nodes, which none of
	// the kind switches below handle; left unresolved they would be assigned an
	// ID but never emitted, producing a dangling type reference on the client.
	t = types.Unalias(t)
	s.collectType(t)
	return s.ids.typeID(t)
}

func (s *serializer) collectType(t types.Type) {
	if t == nil {
		return
	}
	t = types.Unalias(t) // see typeID: never collect a bare *types.Alias
	if s.collectedTypes[t] {
		return // already collected in this call
	}
	if s.ids.isTypeStreamed(t) {
		// Already delivered to client in a prior call; just ensure ID is
		// stable but do not re-emit.
		s.ids.typeID(t)
		return
	}
	s.collectedTypes[t] = true
	// Assign ID first (prevents infinite recursion on cyclic types)
	s.ids.typeID(t)

	// Recurse into sub-types BEFORE adding ourselves to allTypes.
	// This ensures dependencies are serialized before dependents (topological order).
	switch ut := t.(type) {
	case *types.Pointer:
		s.collectType(ut.Elem())
	case *types.Array:
		s.collectType(ut.Elem())
	case *types.Slice:
		s.collectType(ut.Elem())
	case *types.Map:
		s.collectType(ut.Key())
		s.collectType(ut.Elem())
	case *types.Chan:
		s.collectType(ut.Elem())
	case *types.Struct:
		for i := 0; i < ut.NumFields(); i++ {
			s.collectType(ut.Field(i).Type())
		}
	case *types.Interface:
		for i := 0; i < ut.NumMethods(); i++ {
			s.collectType(ut.Method(i).Type())
		}
		for i := 0; i < ut.NumEmbeddeds(); i++ {
			s.collectType(ut.EmbeddedType(i))
		}
	case *types.Signature:
		params := ut.Params()
		for i := 0; i < params.Len(); i++ {
			s.collectType(params.At(i).Type())
		}
		results := ut.Results()
		for i := 0; i < results.Len(); i++ {
			s.collectType(results.At(i).Type())
		}
		if ut.Recv() != nil {
			s.collectType(ut.Recv().Type())
		}
	case *types.Tuple:
		for i := 0; i < ut.Len(); i++ {
			s.collectType(ut.At(i).Type())
		}
	case *types.Named:
		s.collectType(ut.Underlying())
		typeParams := ut.TypeParams()
		if typeParams != nil {
			for i := 0; i < typeParams.Len(); i++ {
				s.collectType(typeParams.At(i))
			}
		}
		// Also collect type args for instantiated generics
		targs := ut.TypeArgs()
		if targs != nil {
			for i := 0; i < targs.Len(); i++ {
				s.collectType(targs.At(i))
			}
		}
	case *types.TypeParam:
		s.collectType(ut.Constraint())
	case *types.Union:
		for i := 0; i < ut.Len(); i++ {
			s.collectType(ut.Term(i).Type())
		}
	}

	// Add to allTypes AFTER sub-types (topological order)
	s.allTypes = append(s.allTypes, t)
}

// ─── Streaming phase 1: Types ───────────────────────────────────────

func (s *serializer) serializeType(t types.Type) *pb.ProtoTypeDefinition {
	t = types.Unalias(t) // aliases are resolved away in typeID/collectType
	id := s.typeID(t)

	switch ut := t.(type) {
	case *types.Basic:
		return &pb.ProtoTypeDefinition{
			Id:   id,
			Type: &pb.ProtoTypeDefinition_Basic{Basic: &pb.ProtoBasicType{Kind: basicKindToProto(ut.Kind())}},
		}
	case *types.Pointer:
		return &pb.ProtoTypeDefinition{
			Id:   id,
			Type: &pb.ProtoTypeDefinition_Pointer{Pointer: &pb.ProtoPointerType{ElemTypeId: s.typeID(ut.Elem())}},
		}
	case *types.Array:
		return &pb.ProtoTypeDefinition{
			Id:   id,
			Type: &pb.ProtoTypeDefinition_Array{Array: &pb.ProtoArrayType{ElemTypeId: s.typeID(ut.Elem()), Length: ut.Len()}},
		}
	case *types.Slice:
		return &pb.ProtoTypeDefinition{
			Id:   id,
			Type: &pb.ProtoTypeDefinition_Slice{Slice: &pb.ProtoSliceType{ElemTypeId: s.typeID(ut.Elem())}},
		}
	case *types.Map:
		return &pb.ProtoTypeDefinition{
			Id:   id,
			Type: &pb.ProtoTypeDefinition_MapType{MapType: &pb.ProtoMapType{KeyTypeId: s.typeID(ut.Key()), ValueTypeId: s.typeID(ut.Elem())}},
		}
	case *types.Chan:
		return &pb.ProtoTypeDefinition{
			Id:   id,
			Type: &pb.ProtoTypeDefinition_ChanType{ChanType: &pb.ProtoChanType{ElemTypeId: s.typeID(ut.Elem()), Direction: chanDirToProto(ut.Dir())}},
		}
	case *types.Struct:
		fields := make([]*pb.ProtoStructField, ut.NumFields())
		for i := 0; i < ut.NumFields(); i++ {
			f := ut.Field(i)
			fields[i] = &pb.ProtoStructField{
				Name:     f.Name(),
				TypeId:   s.typeID(f.Type()),
				Index:    int32(i),
				Embedded: f.Embedded(),
				Exported: f.Exported(),
				Tag:      ut.Tag(i),
			}
		}
		return &pb.ProtoTypeDefinition{
			Id:   id,
			Type: &pb.ProtoTypeDefinition_StructType{StructType: &pb.ProtoStructType{Fields: fields}},
		}
	case *types.Interface:
		methods := make([]*pb.ProtoInterfaceMethod, ut.NumMethods())
		for i := 0; i < ut.NumMethods(); i++ {
			m := ut.Method(i)
			methods[i] = &pb.ProtoInterfaceMethod{
				Name:            m.Name(),
				SignatureTypeId: s.typeID(m.Type()),
			}
		}
		var embedIDs []int32
		for i := 0; i < ut.NumEmbeddeds(); i++ {
			embedIDs = append(embedIDs, s.typeID(ut.EmbeddedType(i)))
		}
		return &pb.ProtoTypeDefinition{
			Id:   id,
			Type: &pb.ProtoTypeDefinition_InterfaceType{InterfaceType: &pb.ProtoInterfaceType{Methods: methods, EmbedTypeIds: embedIDs}},
		}
	case *types.Signature:
		return &pb.ProtoTypeDefinition{
			Id:   id,
			Type: &pb.ProtoTypeDefinition_FuncType{FuncType: s.serializeFuncType(ut)},
		}
	case *types.Named:
		var typeArgIDs []int32
		targs := ut.TypeArgs()
		if targs != nil {
			for i := 0; i < targs.Len(); i++ {
				typeArgIDs = append(typeArgIDs, s.typeID(targs.At(i)))
			}
		}
		return &pb.ProtoTypeDefinition{
			Id: id,
			Type: &pb.ProtoTypeDefinition_NamedRef{NamedRef: &pb.ProtoNamedTypeRef{
				NamedTypeId: s.ids.namedID(ut.Obj()),
				TypeArgIds:  typeArgIDs,
			}},
		}
	case *types.TypeParam:
		return &pb.ProtoTypeDefinition{
			Id: id,
			Type: &pb.ProtoTypeDefinition_TypeParam{TypeParam: &pb.ProtoTypeParam{
				Name:             ut.Obj().Name(),
				Index:            int32(ut.Index()),
				ConstraintTypeId: s.typeID(ut.Constraint()),
			}},
		}
	case *types.Tuple:
		var elemIDs []int32
		for i := 0; i < ut.Len(); i++ {
			elemIDs = append(elemIDs, s.typeID(ut.At(i).Type()))
		}
		return &pb.ProtoTypeDefinition{
			Id:   id,
			Type: &pb.ProtoTypeDefinition_Tuple{Tuple: &pb.ProtoTupleType{ElementTypeIds: elemIDs}},
		}
	case *types.Union:
		// Represent a type-set union as a synthetic interface whose embedded
		// types are the union's terms. This keeps the wire format backward
		// compatible (no new proto field) and lets clients walk the term set
		// via the InterfaceType.embed_type_ids. Methods are left empty —
		// unions only constrain underlying types.
		var embedIDs []int32
		for i := 0; i < ut.Len(); i++ {
			embedIDs = append(embedIDs, s.typeID(ut.Term(i).Type()))
		}
		return &pb.ProtoTypeDefinition{
			Id:   id,
			Type: &pb.ProtoTypeDefinition_InterfaceType{InterfaceType: &pb.ProtoInterfaceType{EmbedTypeIds: embedIDs}},
		}
	default:
		// Check for unsafe.Pointer
		if t.String() == "unsafe.Pointer" {
			return &pb.ProtoTypeDefinition{
				Id:   id,
				Type: &pb.ProtoTypeDefinition_UnsafePointer{UnsafePointer: &pb.ProtoUnsafePointerType{}},
			}
		}
		// SSA-internal opaque types (e.g. range-over-func "iter", deferStack)
		// are unnamed self-underlying values with no public Go representation.
		// Emit them as unsafe.Pointer so referencing instructions still resolve.
		if t.Underlying() == t {
			return &pb.ProtoTypeDefinition{
				Id:   id,
				Type: &pb.ProtoTypeDefinition_UnsafePointer{UnsafePointer: &pb.ProtoUnsafePointerType{}},
			}
		}
		log.Printf("WARN: unhandled type kind: %T", t)
		return nil
	}
}

func (s *serializer) serializeFuncType(sig *types.Signature) *pb.ProtoFuncType {
	ft := &pb.ProtoFuncType{Variadic: sig.Variadic()}
	params := sig.Params()
	for i := 0; i < params.Len(); i++ {
		ft.ParamTypeIds = append(ft.ParamTypeIds, s.typeID(params.At(i).Type()))
	}
	results := sig.Results()
	for i := 0; i < results.Len(); i++ {
		ft.ResultTypeIds = append(ft.ResultTypeIds, s.typeID(results.At(i).Type()))
	}
	if sig.Recv() != nil {
		ft.RecvTypeId = s.typeID(sig.Recv().Type())
	}
	return ft
}

// ─── Streaming phase 2: Packages ────────────────────────────────────

func (s *serializer) serializePackage(pkg *ssa.Package) *pb.ProtoPackage {
	pp := &pb.ProtoPackage{
		Id:         s.ids.packageID(pkg),
		ImportPath: pkg.Pkg.Path(),
		Name:       pkg.Pkg.Name(),
	}

	// Imports. Resolve each *types.Package import to an *ssa.Package across
	// the whole program (not just user packages). External/stdlib imports
	// that have no SSA presence are skipped — they get external stubs
	// emitted via externalPackageStubs() when actually referenced.
	importedSSA := make([]*ssa.Package, 0)
	seen := make(map[string]bool)
	for _, imp := range pkg.Pkg.Imports() {
		if imp == nil || seen[imp.Path()] {
			continue
		}
		seen[imp.Path()] = true
		if sp := lookupSSAPackageByTypesPackage(s.prog, imp); sp != nil {
			pp.ImportIds = append(pp.ImportIds, s.ids.packageID(sp))
			importedSSA = append(importedSSA, sp)
		}
	}
	// Members
	for _, mem := range sortedMembers(pkg) {
		switch m := mem.(type) {
		case *ssa.Function:
			pp.Functions = append(pp.Functions, s.serializeFunction(m))
			s.serializedFunctions[m] = true
		case *ssa.Type:
			pp.NamedTypes = append(pp.NamedTypes, s.serializeNamedType(m.Object().(*types.TypeName)))
		case *ssa.Global:
			pp.Globals = append(pp.Globals, s.serializeGlobal(m))
		case *ssa.NamedConst:
			pp.Constants = append(pp.Constants, s.serializeConst(m))
		}
	}

	// Serialize methods for all named types in this package.
	// Methods are not package-level members in go-ssa, they are obtained
	// from the method sets. We need to serialize them so the Kotlin side
	// can resolve method IDs.
	//
	// IMPORTANT: A type's method set contains both declared methods AND
	// promoted methods inherited from embedded types in OTHER packages.
	// Only attach methods to this package's `pp.Functions` list if they
	// are actually declared here (i.e. declaredPackage(fn) == pkg). The
	// promoted ones will be serialized when their owning package is
	// loaded, and they will be looked up via the receiver-type method
	// list on the Kotlin side. Without this guard, the client-side
	// invariant `fn in pkg.functions ⇒ fn.pkg === pkg` is violated.
	for _, mem := range sortedMembers(pkg) {
		if t, ok := mem.(*ssa.Type); ok {
			named := t.Object().(*types.TypeName)
			// Value receiver methods
			mset := s.prog.MethodSets.MethodSet(named.Type())
			for i := 0; i < mset.Len(); i++ {
				fn := s.prog.MethodValue(mset.At(i))
				if fn == nil || s.serializedFunctions[fn] {
					continue
				}
				if declaredPackage(fn) != pkg {
					continue // promoted from an embedded type in another package
				}
				pf := s.serializeFunction(fn)
				pf.IsMethod = true
				pp.Functions = append(pp.Functions, pf)
				s.serializedFunctions[fn] = true
			}
			// Pointer receiver methods. Skip entries whose declared method
			// is already in the value receiver set: those are SSA-synthetic
			// pointer wrappers around a value-receiver method, not separate
			// user-declared functions, and serializing both would expose two
			// functions with the same name in the package.
			pmset := s.prog.MethodSets.MethodSet(types.NewPointer(named.Type()))
			for i := 0; i < pmset.Len(); i++ {
				sel := pmset.At(i)
				if isInValueMethodSet(mset, sel) {
					continue
				}
				fn := s.prog.MethodValue(sel)
				if fn == nil || s.serializedFunctions[fn] {
					continue
				}
				if declaredPackage(fn) != pkg {
					continue // promoted from an embedded type in another package
				}
				pf := s.serializeFunction(fn)
				pf.IsMethod = true
				pp.Functions = append(pp.Functions, pf)
				s.serializedFunctions[fn] = true
			}
		}
	}

	// Serialize all remaining functions that belong to this package but
	// are not package members (anonymous functions, instantiated generics, etc.).
	// These are discovered via ssautil.AllFunctions and referenced by
	// MakeClosure/Call instructions.
	for _, fn := range s.allFunctions {
		if !s.serializedFunctions[fn] && fn.Package() != nil && fn.Package() == pkg {
			pf := s.serializeFunction(fn)
			pp.Functions = append(pp.Functions, pf)
			s.serializedFunctions[fn] = true
		}
	}

	// Serialize package-less functions whose *declared* package (via
	// Origin/Parent) is this one. This covers instantiated generic
	// functions and methods like `(test.GenBox[string]).Get[string]`, for
	// which `fn.Package()` is nil but `fn.Origin().Pkg` is the package the
	// generic was declared in.
	for _, fn := range s.allFunctions {
		if s.serializedFunctions[fn] || fn.Package() != nil {
			continue
		}
		if declaredPackage(fn) == pkg {
			pf := s.serializeFunction(fn)
			pp.Functions = append(pp.Functions, pf)
			s.serializedFunctions[fn] = true
		}
	}

	// Serialize remaining package-less functions (e.g. synthetic wrappers
	// with no declared package) in the first user package as a fallback.
	// IMPORTANT: only claim functions whose declared package is also nil
	// or is not one of the user packages — otherwise we'd steal
	// instantiated generic methods declared in later user packages,
	// because `serializePackage(s.pkgs[0])` runs before later packages.
	if len(s.pkgs) > 0 && pkg == s.pkgs[0] {
		userPkgs := make(map[*ssa.Package]bool, len(s.pkgs))
		for _, up := range s.pkgs {
			userPkgs[up] = true
		}
		for _, fn := range s.allFunctions {
			if s.serializedFunctions[fn] || fn.Package() != nil {
				continue
			}
			dp := declaredPackage(fn)
			if dp != nil && userPkgs[dp] && dp != pkg {
				continue // belongs to a later user package, let it serialize there
			}
			pf := s.serializeFunction(fn)
			pp.Functions = append(pp.Functions, pf)
			s.serializedFunctions[fn] = true
		}
	}

	// Init function
	initFn := pkg.Func("init")
	if initFn != nil {
		pp.InitFunctionId = s.ids.functionID(initFn)
	}

	return pp
}

func (s *serializer) serializeFunction(fn *ssa.Function) *pb.ProtoFunction {
	fnPkg := fn.Package()
	if fnPkg == nil {
		// For instantiated generic functions/methods fn.Package() is nil,
		// but the declaring package is reachable via Origin()/Parent()/Object.
		fnPkg = declaredPackage(fn)
	}
	// has_body should reflect "there is a body in source", not "a body is
	// currently materialized in this SSA program". For a cross-package callee
	// whose owner was not yet built, fn.Blocks is empty, but the body exists
	// and can be requested via LoadFunctionBody. Synthetic functions have no
	// source body; we treat them as body-less unless they already have blocks.
	hasBody := len(fn.Blocks) > 0 || (fn.Synthetic == "" && fnPkg != nil)
	pf := &pb.ProtoFunction{
		Id:       s.ids.functionID(fn),
		Name:     sanitizeUTF8(fn.Name()),
		FullName: sanitizeUTF8(fn.String()),
		HasBody:  hasBody,
	}

	if fnPkg != nil {
		pf.PackageId = s.ids.packageID(fnPkg)
	}
	pf.SignatureTypeId = s.typeID(fn.Signature)
	if origin := fn.Origin(); origin != nil && origin != fn {
		pf.OriginFunctionId = s.ids.functionID(origin)
	}
	if typeParams := fn.TypeParams(); typeParams != nil {
		for i := 0; i < typeParams.Len(); i++ {
			typeParam := typeParams.At(i)
			pf.TypeParams = append(pf.TypeParams, &pb.ProtoTypeParamDecl{
				Name:             typeParam.Obj().Name(),
				Index:            int32(typeParam.Index()),
				ConstraintTypeId: s.typeID(typeParam.Constraint()),
				TypeId:           s.typeID(typeParam),
			})
		}
	}
	for _, typeArg := range fn.TypeArgs() {
		pf.TypeArgIds = append(pf.TypeArgIds, s.typeID(typeArg))
	}

	// Method info
	if recv := fn.Signature.Recv(); recv != nil {
		pf.IsMethod = true
		recvType := recv.Type()
		if ptr, ok := recvType.(*types.Pointer); ok {
			pf.IsPointerReceiver = true
			recvType = ptr.Elem()
		}
		if named, ok := recvType.(*types.Named); ok {
			pf.ReceiverTypeId = s.typeID(named)
		}
	}

	// Parameters
	for i, p := range fn.Params {
		pf.Params = append(pf.Params, &pb.ProtoParam{
			Name:   p.Name(),
			TypeId: s.typeID(p.Type()),
			Index:  int32(i),
		})
	}

	// Free variables
	for i, fv := range fn.FreeVars {
		pf.FreeVars = append(pf.FreeVars, &pb.ProtoFreeVar{
			Name:   fv.Name(),
			TypeId: s.typeID(fv.Type()),
			Index:  int32(i),
		})
	}

	// Flags
	pf.IsExported = fn.Object() != nil && fn.Object().Exported()
	pf.IsSynthetic = fn.Synthetic != ""
	pf.SyntheticKind = sanitizeUTF8(fn.Synthetic)

	// Closure
	if fn.Parent() != nil {
		pf.ParentFunctionId = s.ids.functionID(fn.Parent())
	}
	for _, anon := range fn.AnonFuncs {
		pf.AnonFunctionIds = append(pf.AnonFunctionIds, s.ids.functionID(anon))
	}

	// Position
	if fn.Pos().IsValid() {
		pf.Position = s.positionOf(fn.Pos())
	}

	s.stats.functionCount++
	return pf
}

func (s *serializer) serializeNamedType(named *types.TypeName) *pb.ProtoNamedType {
	s.emittedNamedTypes[named] = true
	pkgPath := ""
	if named.Pkg() != nil {
		pkgPath = named.Pkg().Path()
	}
	fullName := named.Name()
	if pkgPath != "" {
		fullName = pkgPath + "." + named.Name()
	}
	nt := &pb.ProtoNamedType{
		Id:               s.ids.namedID(named),
		Name:             named.Name(),
		FullName:         fullName,
		UnderlyingTypeId: s.typeID(named.Type().Underlying()),
	}

	// Determine kind
	switch named.Type().Underlying().(type) {
	case *types.Struct:
		nt.Kind = pb.ProtoNamedTypeKind_NAMED_TYPE_STRUCT
	case *types.Interface:
		nt.Kind = pb.ProtoNamedTypeKind_NAMED_TYPE_INTERFACE
	default:
		nt.Kind = pb.ProtoNamedTypeKind_NAMED_TYPE_OTHER
	}

	// Fields (for structs)
	if st, ok := named.Type().Underlying().(*types.Struct); ok {
		for i := 0; i < st.NumFields(); i++ {
			f := st.Field(i)
			nt.Fields = append(nt.Fields, &pb.ProtoFieldDecl{
				Name:     f.Name(),
				TypeId:   s.typeID(f.Type()),
				Index:    int32(i),
				Embedded: f.Embedded(),
				Exported: f.Exported(),
				Tag:      st.Tag(i),
			})
		}
	}

	// Interface methods
	if iface, ok := named.Type().Underlying().(*types.Interface); ok {
		for i := 0; i < iface.NumMethods(); i++ {
			m := iface.Method(i)
			nt.InterfaceMethods = append(nt.InterfaceMethods, &pb.ProtoInterfaceMethodDecl{
				Name:            m.Name(),
				SignatureTypeId: s.typeID(m.Type()),
			})
		}
		for i := 0; i < iface.NumEmbeddeds(); i++ {
			embType := iface.EmbeddedType(i)
			if namedEmb, ok := embType.(*types.Named); ok {
				nt.EmbeddedInterfaceIds = append(nt.EmbeddedInterfaceIds, s.ids.namedID(namedEmb.Obj()))
			}
		}
	}

	// Methods (value receiver). Method sets are queried only for *types.Named;
	// skip aliases and universe types whose Type() is not a *types.Named.
	namedType, _ := named.Type().(*types.Named)
	if namedType == nil {
		return nt
	}
	if typeParams := namedType.TypeParams(); typeParams != nil {
		for i := 0; i < typeParams.Len(); i++ {
			typeParam := typeParams.At(i)
			nt.TypeParams = append(nt.TypeParams, &pb.ProtoTypeParamDecl{
				Name:             typeParam.Obj().Name(),
				Index:            int32(typeParam.Index()),
				ConstraintTypeId: s.typeID(typeParam.Constraint()),
				TypeId:           s.typeID(typeParam),
			})
		}
	}
	mset := s.prog.MethodSets.MethodSet(namedType)
	for i := 0; i < mset.Len(); i++ {
		fn := s.prog.MethodValue(mset.At(i))
		if fn != nil {
			nt.MethodIds = append(nt.MethodIds, s.ids.functionID(fn))
		}
	}

	// Methods (pointer receiver). Skip entries already present in the value
	// receiver set — those are SSA-synthetic pointer wrappers, not separate
	// user-declared methods.
	pmset := s.prog.MethodSets.MethodSet(types.NewPointer(namedType))
	for i := 0; i < pmset.Len(); i++ {
		sel := pmset.At(i)
		if isInValueMethodSet(mset, sel) {
			continue
		}
		fn := s.prog.MethodValue(sel)
		if fn != nil {
			nt.PointerMethodIds = append(nt.PointerMethodIds, s.ids.functionID(fn))
		}
	}

	if named.Pos().IsValid() {
		nt.Position = s.positionOf(named.Pos())
	}

	return nt
}

func (s *serializer) serializeGlobal(g *ssa.Global) *pb.ProtoGlobal {
	pg := &pb.ProtoGlobal{
		Id:         s.ids.globalID(g),
		Name:       g.Name(),
		FullName:   g.String(),
		TypeId:     s.typeID(deref(g.Type())),
		IsExported: g.Object() != nil && g.Object().Exported(),
	}
	if g.Pos().IsValid() {
		pg.Position = s.positionOf(g.Pos())
	}
	return pg
}

func (s *serializer) serializeConst(c *ssa.NamedConst) *pb.ProtoConst {
	pc := &pb.ProtoConst{
		Id:         s.ids.constID(c),
		Name:       c.Name(),
		FullName:   c.String(),
		TypeId:     s.typeID(c.Type()),
		Value:      s.constValueToProto(c.Value.Value),
		IsExported: c.Object().Exported(),
	}
	if c.Pos().IsValid() {
		pc.Position = s.positionOf(c.Pos())
	}
	return pc
}

// ─── Streaming phase 3: Function bodies ─────────────────────────────

func (s *serializer) serializeFunctionBody(fn *ssa.Function) (body *pb.ProtoFunctionBody, err error) {
	defer func() {
		if r := recover(); r != nil {
			err = fmt.Errorf("panic: %v", r)
		}
	}()

	// Build value ID map for this function
	s.funcValueIDs = make(map[ssa.Value]int32)
	nextValueID := int32(1)
	for _, p := range fn.Params {
		s.funcValueIDs[p] = -1 // params use param_index
	}
	for _, fv := range fn.FreeVars {
		s.funcValueIDs[fv] = -1 // free vars use free_var_index
	}

	// Assign value IDs to value-producing instructions
	for _, block := range fn.Blocks {
		for _, inst := range block.Instrs {
			if v, ok := inst.(ssa.Value); ok {
				s.funcValueIDs[v] = nextValueID
				nextValueID++
			}
		}
	}

	body = &pb.ProtoFunctionBody{
		FunctionId:        s.ids.functionID(fn),
		RecoverBlockIndex: -1,
	}

	if fn.Recover != nil {
		body.RecoverBlockIndex = int32(fn.Recover.Index)
	}

	blockStartInstIndex := make(map[*ssa.BasicBlock]int32, len(fn.Blocks))
	instIndex := int32(0)
	for _, block := range fn.Blocks {
		blockStartInstIndex[block] = instIndex
		instIndex += int32(len(block.Instrs))
	}

	instIndex = 0
	for _, block := range fn.Blocks {
		pb_block := &pb.ProtoBasicBlock{
			Index: int32(block.Index),
			Label: block.Comment,
		}

		for _, pred := range block.Preds {
			pb_block.PredIndices = append(pb_block.PredIndices, int32(pred.Index))
		}
		for _, succ := range block.Succs {
			pb_block.SuccIndices = append(pb_block.SuccIndices, int32(succ.Index))
		}

		// Dominator tree
		idom := block.Idom()
		if idom != nil {
			pb_block.IdomIndex = int32(idom.Index)
		} else {
			pb_block.IdomIndex = -1
		}
		for _, d := range block.Dominees() {
			pb_block.DomineeIndices = append(pb_block.DomineeIndices, int32(d.Index))
		}

		for _, inst := range block.Instrs {
			pi := s.serializeInstruction(inst, instIndex, block, blockStartInstIndex)
			pb_block.Instructions = append(pb_block.Instructions, pi)
			instIndex++
			s.stats.instructionCount++
		}

		body.Blocks = append(body.Blocks, pb_block)
	}

	return body, nil
}

func (s *serializer) serializeInstruction(inst ssa.Instruction, idx int32, block *ssa.BasicBlock, blockStartInstIndex map[*ssa.BasicBlock]int32) *pb.ProtoInstruction {
	pi := &pb.ProtoInstruction{
		Index: idx,
	}

	if inst.Pos().IsValid() {
		pi.Position = s.positionOf(inst.Pos())
	}

	// If instruction produces a value
	if v, ok := inst.(ssa.Value); ok {
		pi.ValueId = s.funcValueIDs[v]
		pi.TypeId = s.typeID(v.Type())
		pi.Name = v.Name()
	}

	switch i := inst.(type) {
	case *ssa.Alloc:
		pi.Inst = &pb.ProtoInstruction_Alloc{
			Alloc: &pb.ProtoAllocInst{
				AllocTypeId: s.typeID(deref(i.Type())),
				Heap:        i.Heap,
				Comment:     i.Comment,
			},
		}
	case *ssa.Phi:
		if len(i.Edges) != len(block.Preds) {
			panic(fmt.Sprintf("phi %s in block %d has %d edges but %d predecessors", i.Name(), block.Index, len(i.Edges), len(block.Preds)))
		}
		edges := make([]*pb.ProtoPhiEdge, len(i.Edges))
		for j, e := range i.Edges {
			predInstRef := predecessorTerminatorInstIndex(block.Preds[j], blockStartInstIndex)
			edges[j] = &pb.ProtoPhiEdge{
				PredInstRef: &predInstRef,
				Value:       s.valueRef(e),
			}
		}
		pi.Inst = &pb.ProtoInstruction_Phi{
			Phi: &pb.ProtoPhiInst{Edges: edges, Comment: i.Comment},
		}
	case *ssa.BinOp:
		pi.Inst = &pb.ProtoInstruction_BinOp{
			BinOp: &pb.ProtoBinOpInst{
				Op: tokenToBinOp(i.Op),
				X:  s.valueRef(i.X),
				Y:  s.valueRef(i.Y),
			},
		}
	case *ssa.UnOp:
		pi.Inst = &pb.ProtoInstruction_UnOp{
			UnOp: &pb.ProtoUnOpInst{
				Op:      tokenToUnOp(i.Op),
				X:       s.valueRef(i.X),
				CommaOk: i.CommaOk,
			},
		}
	case *ssa.Call:
		pi.Inst = &pb.ProtoInstruction_Call{
			Call: &pb.ProtoCallInst{Call: s.serializeCallCommon(&i.Call)},
		}
	case *ssa.ChangeType:
		pi.Inst = &pb.ProtoInstruction_ChangeType{
			ChangeType: &pb.ProtoChangeTypeInst{X: s.valueRef(i.X)},
		}
	case *ssa.Convert:
		pi.Inst = &pb.ProtoInstruction_Convert{
			Convert: &pb.ProtoConvertInst{X: s.valueRef(i.X)},
		}
	case *ssa.MultiConvert:
		pi.Inst = &pb.ProtoInstruction_MultiConvert{
			MultiConvert: &pb.ProtoMultiConvertInst{
				X:          s.valueRef(i.X),
				FromTypeId: s.typeID(i.X.Type()),
				ToTypeId:   s.typeID(i.Type()),
			},
		}
	case *ssa.ChangeInterface:
		pi.Inst = &pb.ProtoInstruction_ChangeInterface{
			ChangeInterface: &pb.ProtoChangeInterfaceInst{X: s.valueRef(i.X)},
		}
	case *ssa.SliceToArrayPointer:
		pi.Inst = &pb.ProtoInstruction_SliceToArrayPointer{
			SliceToArrayPointer: &pb.ProtoSliceToArrayPointerInst{X: s.valueRef(i.X)},
		}
	case *ssa.MakeInterface:
		pi.Inst = &pb.ProtoInstruction_MakeInterface{
			MakeInterface: &pb.ProtoMakeInterfaceInst{X: s.valueRef(i.X)},
		}
	case *ssa.MakeClosure:
		bindings := make([]*pb.ProtoValueRef, len(i.Bindings))
		for j, b := range i.Bindings {
			bindings[j] = s.valueRef(b)
		}
		fnVal := i.Fn.(*ssa.Function)
		pi.Inst = &pb.ProtoInstruction_MakeClosure{
			MakeClosure: &pb.ProtoMakeClosureInst{
				FnId:     s.ids.functionID(fnVal),
				Bindings: bindings,
			},
		}
	case *ssa.MakeMap:
		mm := &pb.ProtoMakeMapInst{}
		if i.Reserve != nil {
			mm.Reserve = s.valueRef(i.Reserve)
			mm.HasReserve = true
		}
		pi.Inst = &pb.ProtoInstruction_MakeMap{MakeMap: mm}
	case *ssa.MakeChan:
		pi.Inst = &pb.ProtoInstruction_MakeChan{
			MakeChan: &pb.ProtoMakeChanInst{Size: s.valueRef(i.Size)},
		}
	case *ssa.MakeSlice:
		pi.Inst = &pb.ProtoInstruction_MakeSlice{
			MakeSlice: &pb.ProtoMakeSliceInst{Len: s.valueRef(i.Len), Cap: s.valueRef(i.Cap)},
		}
	case *ssa.FieldAddr:
		pi.Inst = &pb.ProtoInstruction_FieldAddr{
			FieldAddr: &pb.ProtoFieldAddrInst{
				X:          s.valueRef(i.X),
				FieldIndex: int32(i.Field),
				FieldName:  fieldName(deref(i.X.Type()), i.Field),
			},
		}
	case *ssa.Field:
		pi.Inst = &pb.ProtoInstruction_Field{
			Field: &pb.ProtoFieldInst{
				X:          s.valueRef(i.X),
				FieldIndex: int32(i.Field),
				FieldName:  fieldName(i.X.Type(), i.Field),
			},
		}
	case *ssa.IndexAddr:
		pi.Inst = &pb.ProtoInstruction_IndexAddr{
			IndexAddr: &pb.ProtoIndexAddrInst{X: s.valueRef(i.X), Index: s.valueRef(i.Index)},
		}
	case *ssa.Index:
		pi.Inst = &pb.ProtoInstruction_IndexInst{
			IndexInst: &pb.ProtoIndexInst{X: s.valueRef(i.X), Index: s.valueRef(i.Index)},
		}
	case *ssa.Slice:
		si := &pb.ProtoSliceInst{X: s.valueRef(i.X)}
		if i.Low != nil {
			si.Low = s.valueRef(i.Low)
			si.HasLow = true
		}
		if i.High != nil {
			si.High = s.valueRef(i.High)
			si.HasHigh = true
		}
		if i.Max != nil {
			si.Max = s.valueRef(i.Max)
			si.HasMax = true
		}
		pi.Inst = &pb.ProtoInstruction_SliceInst{SliceInst: si}
	case *ssa.Lookup:
		pi.Inst = &pb.ProtoInstruction_Lookup{
			Lookup: &pb.ProtoLookupInst{X: s.valueRef(i.X), Index: s.valueRef(i.Index), CommaOk: i.CommaOk},
		}
	case *ssa.TypeAssert:
		pi.Inst = &pb.ProtoInstruction_TypeAssert{
			TypeAssert: &pb.ProtoTypeAssertInst{
				X:              s.valueRef(i.X),
				AssertedTypeId: s.typeID(i.AssertedType),
				CommaOk:        i.CommaOk,
			},
		}
	case *ssa.Range:
		pi.Inst = &pb.ProtoInstruction_RangeInst{
			RangeInst: &pb.ProtoRangeInst{X: s.valueRef(i.X)},
		}
	case *ssa.Next:
		pi.Inst = &pb.ProtoInstruction_Next{
			Next: &pb.ProtoNextInst{Iter: s.valueRef(i.Iter), IsString: i.IsString},
		}
	case *ssa.Select:
		states := make([]*pb.ProtoSelectState, len(i.States))
		for j, st := range i.States {
			pss := &pb.ProtoSelectState{
				Direction: chanDirToProto(st.Dir),
				Chan:      s.valueRef(st.Chan),
			}
			if st.Send != nil {
				pss.Send = s.valueRef(st.Send)
				pss.HasSend = true
			}
			if st.Pos.IsValid() {
				pss.Position = s.positionOf(st.Pos)
			}
			states[j] = pss
		}
		pi.Inst = &pb.ProtoInstruction_SelectInst{
			SelectInst: &pb.ProtoSelectInst{States: states, Blocking: i.Blocking},
		}
	case *ssa.Extract:
		pi.Inst = &pb.ProtoInstruction_Extract{
			Extract: &pb.ProtoExtractInst{Tuple: s.valueRef(i.Tuple), ExtractIndex: int32(i.Index)},
		}
	// Effect-only instructions
	case *ssa.Jump:
		s.requireBranchSuccCount(block, "Jump", 1)
		target := s.branchTargetInstIndex(block, block.Succs[0], "Jump", 0, blockStartInstIndex)
		pi.Inst = &pb.ProtoInstruction_Jump{Jump: &pb.ProtoJumpInst{Target: int32Ptr(target)}}
	case *ssa.If:
		s.requireBranchSuccCount(block, "If", 2)
		trueBranch := s.branchTargetInstIndex(block, block.Succs[0], "If", 0, blockStartInstIndex)
		falseBranch := s.branchTargetInstIndex(block, block.Succs[1], "If", 1, blockStartInstIndex)
		pi.Inst = &pb.ProtoInstruction_IfInst{
			IfInst: &pb.ProtoIfInst{
				Cond:        s.valueRef(i.Cond),
				TrueBranch:  int32Ptr(trueBranch),
				FalseBranch: int32Ptr(falseBranch),
			},
		}
	case *ssa.Return:
		results := make([]*pb.ProtoValueRef, len(i.Results))
		for j, r := range i.Results {
			results[j] = s.valueRef(r)
		}
		pi.Inst = &pb.ProtoInstruction_ReturnInst{
			ReturnInst: &pb.ProtoReturnInst{Results: results},
		}
	case *ssa.Panic:
		pi.Inst = &pb.ProtoInstruction_PanicInst{
			PanicInst: &pb.ProtoPanicInst{X: s.valueRef(i.X)},
		}
	case *ssa.Store:
		pi.Inst = &pb.ProtoInstruction_Store{
			Store: &pb.ProtoStoreInst{Addr: s.valueRef(i.Addr), Val: s.valueRef(i.Val)},
		}
	case *ssa.MapUpdate:
		pi.Inst = &pb.ProtoInstruction_MapUpdate{
			MapUpdate: &pb.ProtoMapUpdateInst{Map: s.valueRef(i.Map), Key: s.valueRef(i.Key), Value: s.valueRef(i.Value)},
		}
	case *ssa.Send:
		pi.Inst = &pb.ProtoInstruction_Send{
			Send: &pb.ProtoSendInst{Chan: s.valueRef(i.Chan), X: s.valueRef(i.X)},
		}
	case *ssa.Go:
		pi.Inst = &pb.ProtoInstruction_GoInst{
			GoInst: &pb.ProtoGoInst{Call: s.serializeCallCommon(&i.Call)},
		}
	case *ssa.Defer:
		pi.Inst = &pb.ProtoInstruction_DeferInst{
			DeferInst: &pb.ProtoDeferInst{Call: s.serializeCallCommon(&i.Call)},
		}
	case *ssa.RunDefers:
		pi.Inst = &pb.ProtoInstruction_RunDefers{RunDefers: &pb.ProtoRunDefersInst{}}
	case *ssa.DebugRef:
		pi.Inst = &pb.ProtoInstruction_DebugRef{
			DebugRef: &pb.ProtoDebugRefInst{X: s.valueRef(i.X), IsAddr: i.IsAddr},
		}
	default:
		log.Printf("WARN: unhandled instruction type: %T in %v", inst, inst.Parent())
	}

	return pi
}

// ─── Value references ───────────────────────────────────────────────

func (s *serializer) valueRef(v ssa.Value) *pb.ProtoValueRef {
	if v == nil {
		return nil
	}
	ref := &pb.ProtoValueRef{
		TypeId: s.typeID(v.Type()),
	}

	switch val := v.(type) {
	case *ssa.Parameter:
		idx := -1
		for i, p := range val.Parent().Params {
			if p == val {
				idx = i
				break
			}
		}
		ref.Ref = &pb.ProtoValueRef_ParamIndex{ParamIndex: int32(idx)}
	case *ssa.FreeVar:
		idx := -1
		for i, fv := range val.Parent().FreeVars {
			if fv == val {
				idx = i
				break
			}
		}
		ref.Ref = &pb.ProtoValueRef_FreeVarIndex{FreeVarIndex: int32(idx)}
	case *ssa.Const:
		ref.Ref = &pb.ProtoValueRef_ConstVal{ConstVal: s.constValueToProto(val.Value)}
	case *ssa.Global:
		ref.Ref = &pb.ProtoValueRef_GlobalId{GlobalId: s.ids.globalID(val)}
	case *ssa.Function:
		ref.Ref = &pb.ProtoValueRef_FunctionId{FunctionId: s.ids.functionID(val)}
	case *ssa.Builtin:
		ref.Ref = &pb.ProtoValueRef_BuiltinName{BuiltinName: val.Name()}
	default:
		// Must be a value-producing instruction
		if vid, ok := s.funcValueIDs[val]; ok {
			ref.Ref = &pb.ProtoValueRef_InstValueId{InstValueId: vid}
		} else {
			log.Printf("WARN: unknown value ref: %T %v", val, val)
		}
	}

	return ref
}

func (s *serializer) serializeCallCommon(call *ssa.CallCommon) *pb.ProtoCallInfo {
	ci := &pb.ProtoCallInfo{
		ResultTypeId: s.typeID(call.Signature().Results()),
	}

	if call.IsInvoke() {
		ci.Mode = pb.ProtoCallMode_CALL_INVOKE
		ci.Receiver = s.valueRef(call.Value)
		ci.MethodName = call.Method.Name()
		ci.MethodSignatureTypeId = s.typeID(call.Method.Type())
	} else {
		if _, ok := call.Value.(*ssa.Function); ok {
			ci.Mode = pb.ProtoCallMode_CALL_DIRECT
		} else {
			ci.Mode = pb.ProtoCallMode_CALL_DYNAMIC
		}
		ci.Function = s.valueRef(call.Value)
	}

	for _, arg := range call.Args {
		ci.Args = append(ci.Args, s.valueRef(arg))
	}

	return ci
}

// ─── Helpers ────────────────────────────────────────────────────────

func (s *serializer) constValueToProto(val constant.Value) *pb.ProtoConstValue {
	if val == nil {
		return &pb.ProtoConstValue{Value: &pb.ProtoConstValue_NilValue{NilValue: true}}
	}
	switch val.Kind() {
	case constant.Bool:
		b := constant.BoolVal(val)
		return &pb.ProtoConstValue{Value: &pb.ProtoConstValue_BoolValue{BoolValue: b}}
	case constant.String:
		return &pb.ProtoConstValue{Value: &pb.ProtoConstValue_StringValue{StringValue: sanitizeUTF8(constant.StringVal(val))}}
	case constant.Int:
		if v, ok := constant.Int64Val(val); ok {
			return &pb.ProtoConstValue{Value: &pb.ProtoConstValue_IntValue{IntValue: v}}
		}
		// Fallback to string for very large ints
		return &pb.ProtoConstValue{Value: &pb.ProtoConstValue_StringValue{StringValue: val.ExactString()}}
	case constant.Float:
		if v, ok := constant.Float64Val(val); ok {
			return &pb.ProtoConstValue{Value: &pb.ProtoConstValue_FloatValue{FloatValue: v}}
		}
		return &pb.ProtoConstValue{Value: &pb.ProtoConstValue_FloatValue{FloatValue: 0}}
	case constant.Complex:
		re, _ := constant.Float64Val(constant.Real(val))
		im, _ := constant.Float64Val(constant.Imag(val))
		return &pb.ProtoConstValue{Value: &pb.ProtoConstValue_ComplexValue{ComplexValue: &pb.ProtoComplexValue{Real: re, Imag: im}}}
	default:
		return &pb.ProtoConstValue{Value: &pb.ProtoConstValue_NilValue{NilValue: true}}
	}
}

func (s *serializer) positionOf(pos token.Pos) *pb.ProtoPosition {
	p := s.prog.Fset.Position(pos)
	return &pb.ProtoPosition{
		Filename: p.Filename,
		Line:     int32(p.Line),
		Column:   int32(p.Column),
	}
}

// declaredPackage returns the package in which fn is declared. For
// instantiated generic functions/methods fn.Pkg is nil, so we walk to
// fn.Origin() / fn.Parent() to find the original declaring package.
// As a last resort we resolve via the underlying *types.Func against the
// program's known SSA packages.
func declaredPackage(fn *ssa.Function) *ssa.Package {
	if fn == nil {
		return nil
	}
	if fn.Pkg != nil {
		return fn.Pkg
	}
	if p := fn.Parent(); p != nil {
		if dp := declaredPackage(p); dp != nil {
			return dp
		}
	}
	if o := fn.Origin(); o != nil && o != fn {
		if dp := declaredPackage(o); dp != nil {
			return dp
		}
	}
	if obj := fn.Object(); obj != nil {
		if tp := obj.Pkg(); tp != nil {
			if sp := lookupSSAPackageByTypesPackage(fn.Prog, tp); sp != nil {
				return sp
			}
		}
	}
	return nil
}

// lookupSSAPackageByTypesPackage finds the *ssa.Package matching a
// *types.Package by import path. Returns nil if no match is found.
func lookupSSAPackageByTypesPackage(prog *ssa.Program, tp *types.Package) *ssa.Package {
	if prog == nil || tp == nil {
		return nil
	}
	for _, p := range prog.AllPackages() {
		if p != nil && p.Pkg == tp {
			return p
		}
	}
	// Fallback by path in case of duplicate *types.Package instances.
	for _, p := range prog.AllPackages() {
		if p != nil && p.Pkg != nil && p.Pkg.Path() == tp.Path() {
			return p
		}
	}
	return nil
}

func deref(t types.Type) types.Type {
	if ptr, ok := t.(*types.Pointer); ok {
		return ptr.Elem()
	}
	return t
}

func fieldName(t types.Type, index int) string {
	// Strip named type to get underlying struct
	t = t.Underlying()
	if st, ok := t.(*types.Struct); ok && index < st.NumFields() {
		return st.Field(index).Name()
	}
	return fmt.Sprintf("field%d", index)
}

func sortedMembers(pkg *ssa.Package) []ssa.Member {
	var names []string
	for name := range pkg.Members {
		names = append(names, name)
	}
	sort.Strings(names)
	var members []ssa.Member
	for _, name := range names {
		members = append(members, pkg.Members[name])
	}
	return members
}

func basicKindToProto(kind types.BasicKind) pb.ProtoBasicTypeKind {
	switch kind {
	case types.Bool:
		return pb.ProtoBasicTypeKind_BASIC_BOOL
	case types.Int:
		return pb.ProtoBasicTypeKind_BASIC_INT
	case types.Int8:
		return pb.ProtoBasicTypeKind_BASIC_INT8
	case types.Int16:
		return pb.ProtoBasicTypeKind_BASIC_INT16
	case types.Int32:
		return pb.ProtoBasicTypeKind_BASIC_INT32
	case types.Int64:
		return pb.ProtoBasicTypeKind_BASIC_INT64
	case types.Uint:
		return pb.ProtoBasicTypeKind_BASIC_UINT
	case types.Uint8:
		return pb.ProtoBasicTypeKind_BASIC_UINT8
	case types.Uint16:
		return pb.ProtoBasicTypeKind_BASIC_UINT16
	case types.Uint32:
		return pb.ProtoBasicTypeKind_BASIC_UINT32
	case types.Uint64:
		return pb.ProtoBasicTypeKind_BASIC_UINT64
	case types.Float32:
		return pb.ProtoBasicTypeKind_BASIC_FLOAT32
	case types.Float64:
		return pb.ProtoBasicTypeKind_BASIC_FLOAT64
	case types.Complex64:
		return pb.ProtoBasicTypeKind_BASIC_COMPLEX64
	case types.Complex128:
		return pb.ProtoBasicTypeKind_BASIC_COMPLEX128
	case types.String:
		return pb.ProtoBasicTypeKind_BASIC_STRING
	case types.Uintptr:
		return pb.ProtoBasicTypeKind_BASIC_UINTPTR
	case types.UntypedBool:
		return pb.ProtoBasicTypeKind_BASIC_UNTYPED_BOOL
	case types.UntypedInt:
		return pb.ProtoBasicTypeKind_BASIC_UNTYPED_INT
	case types.UntypedRune:
		return pb.ProtoBasicTypeKind_BASIC_UNTYPED_RUNE
	case types.UntypedFloat:
		return pb.ProtoBasicTypeKind_BASIC_UNTYPED_FLOAT
	case types.UntypedComplex:
		return pb.ProtoBasicTypeKind_BASIC_UNTYPED_COMPLEX
	case types.UntypedString:
		return pb.ProtoBasicTypeKind_BASIC_UNTYPED_STRING
	case types.UntypedNil:
		return pb.ProtoBasicTypeKind_BASIC_UNTYPED_NIL
	default:
		return pb.ProtoBasicTypeKind_BASIC_UNKNOWN
	}
}

func tokenToBinOp(tok token.Token) pb.ProtoBinaryOp {
	switch tok {
	case token.ADD:
		return pb.ProtoBinaryOp_BIN_ADD
	case token.SUB:
		return pb.ProtoBinaryOp_BIN_SUB
	case token.MUL:
		return pb.ProtoBinaryOp_BIN_MUL
	case token.QUO:
		return pb.ProtoBinaryOp_BIN_DIV
	case token.REM:
		return pb.ProtoBinaryOp_BIN_REM
	case token.AND:
		return pb.ProtoBinaryOp_BIN_AND
	case token.OR:
		return pb.ProtoBinaryOp_BIN_OR
	case token.XOR:
		return pb.ProtoBinaryOp_BIN_XOR
	case token.SHL:
		return pb.ProtoBinaryOp_BIN_SHL
	case token.SHR:
		return pb.ProtoBinaryOp_BIN_SHR
	case token.AND_NOT:
		return pb.ProtoBinaryOp_BIN_AND_NOT
	case token.EQL:
		return pb.ProtoBinaryOp_BIN_EQ
	case token.NEQ:
		return pb.ProtoBinaryOp_BIN_NEQ
	case token.LSS:
		return pb.ProtoBinaryOp_BIN_LT
	case token.LEQ:
		return pb.ProtoBinaryOp_BIN_LEQ
	case token.GTR:
		return pb.ProtoBinaryOp_BIN_GT
	case token.GEQ:
		return pb.ProtoBinaryOp_BIN_GEQ
	default:
		return pb.ProtoBinaryOp_BIN_ADD // fallback
	}
}

func tokenToUnOp(tok token.Token) pb.ProtoUnaryOp {
	switch tok {
	case token.NOT:
		return pb.ProtoUnaryOp_UN_NOT
	case token.SUB:
		return pb.ProtoUnaryOp_UN_NEG
	case token.XOR:
		return pb.ProtoUnaryOp_UN_XOR
	case token.MUL:
		return pb.ProtoUnaryOp_UN_DEREF
	case token.ARROW:
		return pb.ProtoUnaryOp_UN_ARROW
	default:
		return pb.ProtoUnaryOp_UN_NOT // fallback
	}
}

func chanDirToProto(dir types.ChanDir) pb.ProtoChanDirection {
	switch dir {
	case types.SendRecv:
		return pb.ProtoChanDirection_CHAN_SEND_RECV
	case types.SendOnly:
		return pb.ProtoChanDirection_CHAN_SEND_ONLY
	case types.RecvOnly:
		return pb.ProtoChanDirection_CHAN_RECV_ONLY
	default:
		return pb.ProtoChanDirection_CHAN_SEND_RECV
	}
}

func int32Ptr(v int32) *int32 {
	return &v
}

func (s *serializer) requireBranchSuccCount(block *ssa.BasicBlock, kind string, expected int) {
	if len(block.Succs) != expected {
		panic(fmt.Sprintf(
			"Go SSA CFG invariant failed in function %s block %d: %s has %d successors, expected %d",
			block.Parent().String(), block.Index, kind, len(block.Succs), expected,
		))
	}
}

func predecessorTerminatorInstIndex(pred *ssa.BasicBlock, blockStartInstIndex map[*ssa.BasicBlock]int32) int32 {
	if len(pred.Instrs) == 0 {
		panic(fmt.Sprintf(
			"Go SSA CFG invariant failed in function %s block %d: phi predecessor has no instructions",
			pred.Parent().String(), pred.Index,
		))
	}
	start, ok := blockStartInstIndex[pred]
	if !ok {
		panic(fmt.Sprintf(
			"Go SSA CFG invariant failed in function %s block %d: phi predecessor has no start instruction index",
			pred.Parent().String(), pred.Index,
		))
	}
	return start + int32(len(pred.Instrs)-1)
}

func (s *serializer) branchTargetInstIndex(block *ssa.BasicBlock, succ *ssa.BasicBlock, kind string, succPos int, blockStartInstIndex map[*ssa.BasicBlock]int32) int32 {
	if len(succ.Instrs) == 0 {
		panic(fmt.Sprintf(
			"Go SSA CFG invariant failed in function %s block %d: %s successor[%d] block %d has no instructions",
			block.Parent().String(), block.Index, kind, succPos, succ.Index,
		))
	}
	target, ok := blockStartInstIndex[succ]
	if !ok {
		panic(fmt.Sprintf(
			"Go SSA CFG invariant failed in function %s block %d: %s successor[%d] block %d has no start instruction index",
			block.Parent().String(), block.Index, kind, succPos, succ.Index,
		))
	}
	return target
}
