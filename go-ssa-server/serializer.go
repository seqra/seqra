package server

import (
	"fmt"
	"go/constant"
	"go/token"
	"go/types"
	"log"
	"sort"

	"golang.org/x/tools/go/ssa"
	"golang.org/x/tools/go/ssa/ssautil"

	pb "github.com/opentaint/go-ir/go-ssa-server/proto/goir"
)

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

	// Maps for value IDs within a function
	funcValueIDs map[ssa.Value]int32

	stats serializerStats
}

func newSerializer(prog *ssa.Program, pkgs []*ssa.Package) *serializer {
	s := &serializer{
		prog: prog,
		pkgs: pkgs,
		ids:  newIDAllocator(),
	}
	s.collectAll()
	return s
}

// ─── Collection phase ───────────────────────────────────────────────

func (s *serializer) collectAll() {
	// Pre-assign IDs for all packages
	for _, pkg := range s.pkgs {
		s.ids.packageID(pkg)
	}

	// Collect all functions (for ID assignment)
	for _, pkg := range s.pkgs {
		for _, mem := range sortedMembers(pkg) {
			switch m := mem.(type) {
			case *ssa.Function:
				s.collectFunction(m)
			case *ssa.Type:
				// Methods are collected via named types below
			case *ssa.Global:
				s.ids.globalID(m)
				s.collectType(m.Type())
			case *ssa.NamedConst:
				s.ids.constID(m)
				s.collectType(m.Type())
			}
		}
		// Collect types and their methods
		for _, mem := range sortedMembers(pkg) {
			if t, ok := mem.(*ssa.Type); ok {
				named := t.Object().(*types.TypeName)
				s.ids.namedID(named)
				s.collectType(named.Type())
				s.collectType(named.Type().Underlying())
				// Collect methods
				mset := s.prog.MethodSets.MethodSet(named.Type())
				for i := 0; i < mset.Len(); i++ {
					fn := s.prog.MethodValue(mset.At(i))
					if fn != nil {
						s.collectFunction(fn)
					}
				}
				pmset := s.prog.MethodSets.MethodSet(types.NewPointer(named.Type()))
				for i := 0; i < pmset.Len(); i++ {
					fn := s.prog.MethodValue(pmset.At(i))
					if fn != nil {
						s.collectFunction(fn)
					}
				}
			}
		}
	}

	// Collect all functions reachable (includes anonymous functions)
	for fn := range ssautil.AllFunctions(s.prog) {
		if fn.Package() != nil {
			s.collectFunction(fn)
		}
	}
}

func (s *serializer) collectFunction(fn *ssa.Function) {
	if _, ok := s.ids.functionIDs[fn]; ok {
		return // already collected
	}
	s.ids.functionID(fn)
	s.collectType(fn.Signature)

	// Collect types from parameters
	for _, p := range fn.Params {
		s.collectType(p.Type())
	}
	for _, fv := range fn.FreeVars {
		s.collectType(fv.Type())
	}

	if len(fn.Blocks) > 0 {
		s.allFunctions = append(s.allFunctions, fn)
		// Collect types from instructions
		for _, block := range fn.Blocks {
			for _, inst := range block.Instrs {
				s.collectInstructionTypes(inst)
			}
		}
	}

	// Collect anonymous functions
	for _, anon := range fn.AnonFuncs {
		s.collectFunction(anon)
	}
}

func (s *serializer) collectInstructionTypes(inst ssa.Instruction) {
	if v, ok := inst.(ssa.Value); ok {
		s.collectType(v.Type())
	}
	for _, op := range inst.Operands(nil) {
		if *op != nil {
			s.collectType((*op).Type())
		}
	}
}

func (s *serializer) collectType(t types.Type) {
	if t == nil {
		return
	}
	if _, ok := s.ids.typeIDs[t]; ok {
		return // already collected
	}
	// Assign ID (which registers it)
	s.ids.typeID(t)
	s.allTypes = append(s.allTypes, t)

	// Recurse into sub-types
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
		// Also collect type args for instantiated generics
		targs := ut.TypeArgs()
		if targs != nil {
			for i := 0; i < targs.Len(); i++ {
				s.collectType(targs.At(i))
			}
		}
	case *types.TypeParam:
		s.collectType(ut.Constraint())
	}
}

// ─── Streaming phase 1: Types ───────────────────────────────────────

func (s *serializer) streamTypes(stream pb.GoSSAService_BuildProgramServer) error {
	for _, t := range s.allTypes {
		td := s.serializeType(t)
		if td == nil {
			continue
		}
		if err := stream.Send(&pb.BuildProgramResponse{
			Payload: &pb.BuildProgramResponse_TypeDef{TypeDef: td},
		}); err != nil {
			return err
		}
		s.stats.typeCount++
	}
	return nil
}

func (s *serializer) serializeType(t types.Type) *pb.ProtoTypeDefinition {
	id := s.ids.typeID(t)

	switch ut := t.(type) {
	case *types.Basic:
		return &pb.ProtoTypeDefinition{
			Id:   id,
			Type: &pb.ProtoTypeDefinition_Basic{Basic: &pb.ProtoBasicType{Kind: basicKindToProto(ut.Kind())}},
		}
	case *types.Pointer:
		return &pb.ProtoTypeDefinition{
			Id:   id,
			Type: &pb.ProtoTypeDefinition_Pointer{Pointer: &pb.ProtoPointerType{ElemTypeId: s.ids.typeID(ut.Elem())}},
		}
	case *types.Array:
		return &pb.ProtoTypeDefinition{
			Id:   id,
			Type: &pb.ProtoTypeDefinition_Array{Array: &pb.ProtoArrayType{ElemTypeId: s.ids.typeID(ut.Elem()), Length: ut.Len()}},
		}
	case *types.Slice:
		return &pb.ProtoTypeDefinition{
			Id:   id,
			Type: &pb.ProtoTypeDefinition_Slice{Slice: &pb.ProtoSliceType{ElemTypeId: s.ids.typeID(ut.Elem())}},
		}
	case *types.Map:
		return &pb.ProtoTypeDefinition{
			Id:   id,
			Type: &pb.ProtoTypeDefinition_MapType{MapType: &pb.ProtoMapType{KeyTypeId: s.ids.typeID(ut.Key()), ValueTypeId: s.ids.typeID(ut.Elem())}},
		}
	case *types.Chan:
		return &pb.ProtoTypeDefinition{
			Id:   id,
			Type: &pb.ProtoTypeDefinition_ChanType{ChanType: &pb.ProtoChanType{ElemTypeId: s.ids.typeID(ut.Elem()), Direction: chanDirToProto(ut.Dir())}},
		}
	case *types.Struct:
		fields := make([]*pb.ProtoStructField, ut.NumFields())
		for i := 0; i < ut.NumFields(); i++ {
			f := ut.Field(i)
			fields[i] = &pb.ProtoStructField{
				Name:     f.Name(),
				TypeId:   s.ids.typeID(f.Type()),
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
				SignatureTypeId: s.ids.typeID(m.Type()),
			}
		}
		var embedIDs []int32
		for i := 0; i < ut.NumEmbeddeds(); i++ {
			embedIDs = append(embedIDs, s.ids.typeID(ut.EmbeddedType(i)))
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
				typeArgIDs = append(typeArgIDs, s.ids.typeID(targs.At(i)))
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
				ConstraintTypeId: s.ids.typeID(ut.Constraint()),
			}},
		}
	case *types.Tuple:
		var elemIDs []int32
		for i := 0; i < ut.Len(); i++ {
			elemIDs = append(elemIDs, s.ids.typeID(ut.At(i).Type()))
		}
		return &pb.ProtoTypeDefinition{
			Id:   id,
			Type: &pb.ProtoTypeDefinition_Tuple{Tuple: &pb.ProtoTupleType{ElementTypeIds: elemIDs}},
		}
	default:
		// Check for unsafe.Pointer
		if t.String() == "unsafe.Pointer" {
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
		ft.ParamTypeIds = append(ft.ParamTypeIds, s.ids.typeID(params.At(i).Type()))
	}
	results := sig.Results()
	for i := 0; i < results.Len(); i++ {
		ft.ResultTypeIds = append(ft.ResultTypeIds, s.ids.typeID(results.At(i).Type()))
	}
	if sig.Recv() != nil {
		ft.RecvTypeId = s.ids.typeID(sig.Recv().Type())
	}
	return ft
}

// ─── Streaming phase 2: Packages ────────────────────────────────────

func (s *serializer) streamPackages(stream pb.GoSSAService_BuildProgramServer) error {
	for _, pkg := range s.pkgs {
		pp := s.serializePackage(pkg)
		if err := stream.Send(&pb.BuildProgramResponse{
			Payload: &pb.BuildProgramResponse_PackageDef{PackageDef: pp},
		}); err != nil {
			return err
		}
		s.stats.packageCount++
	}
	return nil
}

func (s *serializer) serializePackage(pkg *ssa.Package) *pb.ProtoPackage {
	pp := &pb.ProtoPackage{
		Id:         s.ids.packageID(pkg),
		ImportPath: pkg.Pkg.Path(),
		Name:       pkg.Pkg.Name(),
	}

	// Imports
	scope := pkg.Pkg.Scope()
	seen := make(map[string]bool)
	for _, name := range scope.Names() {
		obj := scope.Lookup(name)
		if pn, ok := obj.(*types.PkgName); ok {
			imp := pn.Imported()
			if !seen[imp.Path()] {
				seen[imp.Path()] = true
				// Find the SSA package for this import
				for _, p := range s.pkgs {
					if p.Pkg.Path() == imp.Path() {
						pp.ImportIds = append(pp.ImportIds, s.ids.packageID(p))
						break
					}
				}
			}
		}
	}

	// Members
	for _, mem := range sortedMembers(pkg) {
		switch m := mem.(type) {
		case *ssa.Function:
			pp.Functions = append(pp.Functions, s.serializeFunction(m))
		case *ssa.Type:
			pp.NamedTypes = append(pp.NamedTypes, s.serializeNamedType(pkg, m))
		case *ssa.Global:
			pp.Globals = append(pp.Globals, s.serializeGlobal(m))
		case *ssa.NamedConst:
			pp.Constants = append(pp.Constants, s.serializeConst(m))
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
	pf := &pb.ProtoFunction{
		Id:       s.ids.functionID(fn),
		Name:     fn.Name(),
		FullName: fn.String(),
		HasBody:  len(fn.Blocks) > 0,
	}

	if fn.Package() != nil {
		pf.PackageId = s.ids.packageID(fn.Package())
	}
	pf.SignatureTypeId = s.ids.typeID(fn.Signature)

	// Method info
	if recv := fn.Signature.Recv(); recv != nil {
		pf.IsMethod = true
		recvType := recv.Type()
		if ptr, ok := recvType.(*types.Pointer); ok {
			pf.IsPointerReceiver = true
			recvType = ptr.Elem()
		}
		if named, ok := recvType.(*types.Named); ok {
			pf.ReceiverTypeId = s.ids.typeID(named)
		}
	}

	// Parameters
	for i, p := range fn.Params {
		pf.Params = append(pf.Params, &pb.ProtoParam{
			Name:   p.Name(),
			TypeId: s.ids.typeID(p.Type()),
			Index:  int32(i),
		})
	}

	// Free variables
	for i, fv := range fn.FreeVars {
		pf.FreeVars = append(pf.FreeVars, &pb.ProtoFreeVar{
			Name:   fv.Name(),
			TypeId: s.ids.typeID(fv.Type()),
			Index:  int32(i),
		})
	}

	// Flags
	pf.IsExported = fn.Object() != nil && fn.Object().Exported()
	pf.IsSynthetic = fn.Synthetic != ""
	pf.SyntheticKind = fn.Synthetic

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

func (s *serializer) serializeNamedType(pkg *ssa.Package, t *ssa.Type) *pb.ProtoNamedType {
	named := t.Object().(*types.TypeName)
	nt := &pb.ProtoNamedType{
		Id:               s.ids.namedID(named),
		Name:             named.Name(),
		FullName:         named.Pkg().Path() + "." + named.Name(),
		UnderlyingTypeId: s.ids.typeID(named.Type().Underlying()),
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
				TypeId:   s.ids.typeID(f.Type()),
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
				SignatureTypeId: s.ids.typeID(m.Type()),
			})
		}
		for i := 0; i < iface.NumEmbeddeds(); i++ {
			embType := iface.EmbeddedType(i)
			if namedEmb, ok := embType.(*types.Named); ok {
				nt.EmbeddedInterfaceIds = append(nt.EmbeddedInterfaceIds, s.ids.namedID(namedEmb.Obj()))
			}
		}
	}

	// Methods (value receiver)
	mset := s.prog.MethodSets.MethodSet(named.Type())
	for i := 0; i < mset.Len(); i++ {
		fn := s.prog.MethodValue(mset.At(i))
		if fn != nil {
			nt.MethodIds = append(nt.MethodIds, s.ids.functionID(fn))
		}
	}

	// Methods (pointer receiver)
	pmset := s.prog.MethodSets.MethodSet(types.NewPointer(named.Type()))
	for i := 0; i < pmset.Len(); i++ {
		sel := pmset.At(i)
		// Only include methods that are *not* in the value receiver set
		fn := s.prog.MethodValue(sel)
		if fn != nil {
			found := false
			for j := 0; j < mset.Len(); j++ {
				if mset.At(j).Obj() == sel.Obj() {
					found = true
					break
				}
			}
			if !found {
				nt.PointerMethodIds = append(nt.PointerMethodIds, s.ids.functionID(fn))
			}
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
		TypeId:     s.ids.typeID(deref(g.Type())),
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
		TypeId:     s.ids.typeID(c.Type()),
		Value:      s.constValueToProto(c.Value.Value),
		IsExported: c.Object().Exported(),
	}
	if c.Pos().IsValid() {
		pc.Position = s.positionOf(c.Pos())
	}
	return pc
}

// ─── Streaming phase 3: Function bodies ─────────────────────────────

func (s *serializer) streamFunctionBodies(stream pb.GoSSAService_BuildProgramServer) error {
	for _, fn := range s.allFunctions {
		if len(fn.Blocks) == 0 {
			continue
		}

		body, err := s.serializeFunctionBody(fn)
		if err != nil {
			// Non-fatal: report error and continue
			stream.Send(&pb.BuildProgramResponse{
				Payload: &pb.BuildProgramResponse_Error{
					Error: &pb.ProtoError{
						Message:      fmt.Sprintf("serializing %s: %v", fn.String(), err),
						FunctionName: fn.String(),
						Fatal:        false,
					},
				},
			})
			continue
		}

		if err := stream.Send(&pb.BuildProgramResponse{
			Payload: &pb.BuildProgramResponse_FunctionBody{FunctionBody: body},
		}); err != nil {
			return err
		}
	}
	return nil
}

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

	instIndex := int32(0)
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
			pi := s.serializeInstruction(inst, instIndex)
			pb_block.Instructions = append(pb_block.Instructions, pi)
			instIndex++
			s.stats.instructionCount++
		}

		body.Blocks = append(body.Blocks, pb_block)
	}

	return body, nil
}

func (s *serializer) serializeInstruction(inst ssa.Instruction, idx int32) *pb.ProtoInstruction {
	pi := &pb.ProtoInstruction{
		Index: idx,
	}

	if inst.Pos().IsValid() {
		pi.Position = s.positionOf(inst.Pos())
	}

	// If instruction produces a value
	if v, ok := inst.(ssa.Value); ok {
		pi.ValueId = s.funcValueIDs[v]
		pi.TypeId = s.ids.typeID(v.Type())
		pi.Name = v.Name()
	}

	switch i := inst.(type) {
	case *ssa.Alloc:
		pi.Inst = &pb.ProtoInstruction_Alloc{
			Alloc: &pb.ProtoAllocInst{
				AllocTypeId: s.ids.typeID(deref(i.Type())),
				Heap:        i.Heap,
				Comment:     i.Comment,
			},
		}
	case *ssa.Phi:
		edges := make([]*pb.ProtoValueRef, len(i.Edges))
		for j, e := range i.Edges {
			edges[j] = s.valueRef(e)
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
				FromTypeId: s.ids.typeID(i.X.Type()),
				ToTypeId:   s.ids.typeID(i.Type()),
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
				AssertedTypeId: s.ids.typeID(i.AssertedType),
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
		pi.Inst = &pb.ProtoInstruction_Jump{Jump: &pb.ProtoJumpInst{}}
	case *ssa.If:
		pi.Inst = &pb.ProtoInstruction_IfInst{
			IfInst: &pb.ProtoIfInst{Cond: s.valueRef(i.Cond)},
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
		TypeId: s.ids.typeID(v.Type()),
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
		ResultTypeId: s.ids.typeID(call.Signature().Results()),
	}

	if call.IsInvoke() {
		ci.Mode = pb.ProtoCallMode_CALL_INVOKE
		ci.Receiver = s.valueRef(call.Value)
		ci.MethodName = call.Method.Name()
		ci.MethodSignatureTypeId = s.ids.typeID(call.Method.Type())
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
		return &pb.ProtoConstValue{Value: &pb.ProtoConstValue_StringValue{StringValue: constant.StringVal(val)}}
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
