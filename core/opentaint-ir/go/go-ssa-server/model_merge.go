package server

import (
	"fmt"
	"sort"
	"strconv"
	"strings"

	pb "github.com/opentaint/go-ir/go-ssa-server/proto/goir"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/reflect/protoreflect"
)

// A model package uses opentaint/<target package> as its import path.
// Remove opentaint/ to get the exact target import path. This rule works for
// standard and module packages such as opentaint/strings, opentaint/net/http,
// and opentaint/github.com/acme/lib.
const goModelPrefix = "opentaint/"

type modelMergeState struct {
	modeledPackages  map[string]string
	modeledFunctions map[int32]string
}

func newModelMergeState() *modelMergeState {
	return &modelMergeState{
		modeledPackages:  make(map[string]string),
		modeledFunctions: make(map[int32]string),
	}
}

type modelPathNormalizer struct {
	replacements map[string]string
	ordered      []string
	targets      map[string]bool
}

func modelNormalizer(program *pb.ProtoProgram) (*modelPathNormalizer, error) {
	n := &modelPathNormalizer{
		replacements: make(map[string]string),
		targets:      make(map[string]bool),
	}
	for _, pkg := range program.Packages {
		if pkg.IsDependency || pkg.IsStdlib || pkg.ImportPath == universePackagePath {
			continue
		}
		if !strings.HasPrefix(pkg.ImportPath, goModelPrefix) {
			return nil, fmt.Errorf(
				"Go model package %q is not model-addressed; its import path must be %s<target-import-path>",
				pkg.ImportPath, goModelPrefix,
			)
		}
		target := strings.TrimPrefix(pkg.ImportPath, goModelPrefix)
		if target == "" {
			return nil, fmt.Errorf("Go model package %q has an empty target package", pkg.ImportPath)
		}
		n.replacements[pkg.ImportPath] = target
		n.targets[target] = true
		n.ordered = append(n.ordered, pkg.ImportPath)
	}
	if len(n.replacements) == 0 {
		return nil, fmt.Errorf(
			"Go model contains no model-addressed packages; expected at least one package under %s",
			goModelPrefix,
		)
	}
	sort.Slice(n.ordered, func(i, j int) bool { return len(n.ordered[i]) > len(n.ordered[j]) })
	return n, nil
}

func (n *modelPathNormalizer) path(value string) string {
	if replacement, ok := n.replacements[value]; ok {
		return replacement
	}
	return value
}

func (n *modelPathNormalizer) symbol(value string) string {
	for _, markerPath := range n.ordered {
		value = strings.ReplaceAll(value, markerPath, n.replacements[markerPath])
	}
	return value
}

type protoIndex struct {
	program *pb.ProtoProgram
	norm    *modelPathNormalizer

	packages map[int32]*pb.ProtoPackage
	types    map[int32]*pb.ProtoTypeDefinition
	named    map[int32]*pb.ProtoNamedType
	funcs    map[int32]*pb.ProtoFunction
	globals  map[int32]*pb.ProtoGlobal
	consts   map[int32]*pb.ProtoConst

	packageKey map[int32]string
	namedKey   map[int32]string
	typeMemo   map[int32]string
	typeActive map[int32]bool
}

func indexProto(program *pb.ProtoProgram, norm *modelPathNormalizer) *protoIndex {
	idx := &protoIndex{
		program:    program,
		norm:       norm,
		packages:   make(map[int32]*pb.ProtoPackage),
		types:      make(map[int32]*pb.ProtoTypeDefinition),
		named:      make(map[int32]*pb.ProtoNamedType),
		funcs:      make(map[int32]*pb.ProtoFunction),
		globals:    make(map[int32]*pb.ProtoGlobal),
		consts:     make(map[int32]*pb.ProtoConst),
		packageKey: make(map[int32]string),
		namedKey:   make(map[int32]string),
		typeMemo:   make(map[int32]string),
		typeActive: make(map[int32]bool),
	}
	for _, td := range program.Types {
		idx.types[td.Id] = td
	}
	for _, pkg := range program.Packages {
		idx.packages[pkg.Id] = pkg
		pkgPath := pkg.ImportPath
		if norm != nil {
			pkgPath = norm.path(pkgPath)
		}
		idx.packageKey[pkg.Id] = pkgPath
		for _, named := range pkg.NamedTypes {
			idx.named[named.Id] = named
			idx.namedKey[named.Id] = pkgPath + "." + named.Name
		}
		for _, fn := range pkg.Functions {
			idx.funcs[fn.Id] = fn
		}
		for _, global := range pkg.Globals {
			idx.globals[global.Id] = global
		}
		for _, c := range pkg.Constants {
			idx.consts[c.Id] = c
		}
	}
	return idx
}

func (i *protoIndex) symbol(value string) string {
	if i.norm == nil {
		return value
	}
	return i.norm.symbol(value)
}

func (i *protoIndex) typeKey(id int32) string {
	if id == 0 {
		return "void"
	}
	if key, ok := i.typeMemo[id]; ok {
		return key
	}
	if i.typeActive[id] {
		// Named references break every ordinary recursive Go type cycle.  This
		// fallback is for malformed or future wire types and remains stable
		// across independently loaded programs.
		return "<recursive>"
	}
	td := i.types[id]
	if td == nil {
		return "<missing-type>"
	}
	i.typeActive[id] = true
	defer delete(i.typeActive, id)

	var key string
	switch {
	case td.GetBasic() != nil:
		key = "basic:" + strconv.Itoa(int(td.GetBasic().Kind))
	case td.GetPointer() != nil:
		key = "pointer:" + i.typeKey(td.GetPointer().ElemTypeId)
	case td.GetArray() != nil:
		key = "array:" + strconv.FormatInt(td.GetArray().Length, 10) + ":" + i.typeKey(td.GetArray().ElemTypeId)
	case td.GetSlice() != nil:
		key = "slice:" + i.typeKey(td.GetSlice().ElemTypeId)
	case td.GetMapType() != nil:
		key = "map:" + i.typeKey(td.GetMapType().KeyTypeId) + ":" + i.typeKey(td.GetMapType().ValueTypeId)
	case td.GetChanType() != nil:
		key = "chan:" + strconv.Itoa(int(td.GetChanType().Direction)) + ":" + i.typeKey(td.GetChanType().ElemTypeId)
	case td.GetStructType() != nil:
		var b strings.Builder
		b.WriteString("struct{")
		for _, field := range td.GetStructType().Fields {
			fmt.Fprintf(&b, "%s:%s:%t:%t:%s;", field.Name, i.typeKey(field.TypeId), field.Embedded, field.Exported, field.Tag)
		}
		b.WriteByte('}')
		key = b.String()
	case td.GetInterfaceType() != nil:
		var b strings.Builder
		b.WriteString("interface{")
		for _, method := range td.GetInterfaceType().Methods {
			fmt.Fprintf(&b, "%s:%s;", method.Name, i.typeKey(method.SignatureTypeId))
		}
		for _, embedded := range td.GetInterfaceType().EmbedTypeIds {
			b.WriteString("embed:")
			b.WriteString(i.typeKey(embedded))
			b.WriteByte(';')
		}
		b.WriteByte('}')
		key = b.String()
	case td.GetFuncType() != nil:
		fn := td.GetFuncType()
		var b strings.Builder
		b.WriteString("func(")
		for _, param := range fn.ParamTypeIds {
			b.WriteString(i.typeKey(param))
			b.WriteByte(',')
		}
		b.WriteString(")->(")
		for _, result := range fn.ResultTypeIds {
			b.WriteString(i.typeKey(result))
			b.WriteByte(',')
		}
		fmt.Fprintf(&b, "):variadic=%t:recv=%s", fn.Variadic, i.typeKey(fn.RecvTypeId))
		key = b.String()
	case td.GetNamedRef() != nil:
		ref := td.GetNamedRef()
		var b strings.Builder
		b.WriteString("named:")
		b.WriteString(i.namedKey[ref.NamedTypeId])
		b.WriteByte('[')
		for _, arg := range ref.TypeArgIds {
			b.WriteString(i.typeKey(arg))
			b.WriteByte(',')
		}
		b.WriteByte(']')
		key = b.String()
	case td.GetTypeParam() != nil:
		param := td.GetTypeParam()
		key = fmt.Sprintf("typeparam:%s:%d:%s", param.Name, param.Index, i.typeKey(param.ConstraintTypeId))
	case td.GetTuple() != nil:
		var b strings.Builder
		b.WriteString("tuple(")
		for _, elem := range td.GetTuple().ElementTypeIds {
			b.WriteString(i.typeKey(elem))
			b.WriteByte(',')
		}
		b.WriteByte(')')
		key = b.String()
	case td.GetUnsafePointer() != nil:
		key = "unsafe.Pointer"
	default:
		key = "<unknown-type>"
	}
	i.typeMemo[id] = key
	return key
}

type idRemap struct {
	types     map[int32]int32
	packages  map[int32]int32
	functions map[int32]int32
	named     map[int32]int32
	globals   map[int32]int32
	consts    map[int32]int32
}

func newIDRemap() *idRemap {
	return &idRemap{
		types:     make(map[int32]int32),
		packages:  make(map[int32]int32),
		functions: make(map[int32]int32),
		named:     make(map[int32]int32),
		globals:   make(map[int32]int32),
		consts:    make(map[int32]int32),
	}
}

type idCounters struct {
	typeID, packageID, functionID, namedID, globalID, constID int32
}

func countersFor(program *pb.ProtoProgram) idCounters {
	var result idCounters
	for _, td := range program.Types {
		result.typeID = max(result.typeID, td.Id)
	}
	for _, pkg := range program.Packages {
		result.packageID = max(result.packageID, pkg.Id)
		for _, named := range pkg.NamedTypes {
			result.namedID = max(result.namedID, named.Id)
		}
		for _, fn := range pkg.Functions {
			result.functionID = max(result.functionID, fn.Id)
		}
		for _, global := range pkg.Globals {
			result.globalID = max(result.globalID, global.Id)
		}
		for _, c := range pkg.Constants {
			result.constID = max(result.constID, c.Id)
		}
	}
	return result
}

func reversePackageKeys(i *protoIndex) map[string]int32 {
	result := make(map[string]int32, len(i.packageKey))
	for id, key := range i.packageKey {
		result[key] = id
	}
	return result
}

func typeKeysByID(i *protoIndex) map[int32]string {
	result := make(map[int32]string, len(i.types))
	for id := range i.types {
		result[id] = i.typeKey(id)
	}
	return result
}

func functionKeysByID(i *protoIndex) map[int32]string {
	result := make(map[int32]string, len(i.funcs))
	for id, fn := range i.funcs {
		result[id] = i.symbol(fn.FullName)
	}
	return result
}

func globalKeysByID(i *protoIndex) map[int32]string {
	result := make(map[int32]string, len(i.globals))
	for id, global := range i.globals {
		result[id] = i.symbol(global.FullName)
	}
	return result
}

func constKeysByID(i *protoIndex) map[int32]string {
	result := make(map[int32]string, len(i.consts))
	for id, c := range i.consts {
		result[id] = i.symbol(c.FullName)
	}
	return result
}

func reverseNamedKeys(i *protoIndex) map[string]int32 {
	result := make(map[string]int32, len(i.namedKey))
	for id, key := range i.namedKey {
		result[key] = id
	}
	return result
}

func reverseTypeKeys(i *protoIndex) map[string]int32 {
	result := make(map[string]int32, len(i.types))
	for id := range i.types {
		result[i.typeKey(id)] = id
	}
	return result
}

func reverseFunctionKeys(i *protoIndex) map[string]int32 {
	result := make(map[string]int32, len(i.funcs))
	for id, fn := range i.funcs {
		result[i.symbol(fn.FullName)] = id
	}
	return result
}

func reverseGlobalKeys(i *protoIndex) map[string]int32 {
	result := make(map[string]int32, len(i.globals))
	for id, global := range i.globals {
		result[i.symbol(global.FullName)] = id
	}
	return result
}

func reverseConstKeys(i *protoIndex) map[string]int32 {
	result := make(map[string]int32, len(i.consts))
	for id, c := range i.consts {
		result[i.symbol(c.FullName)] = id
	}
	return result
}

func assignIDs[K comparable](source map[int32]K, existing map[K]int32, next *int32) map[int32]int32 {
	result := make(map[int32]int32, len(source))
	oldIDs := make([]int, 0, len(source))
	for oldID := range source {
		oldIDs = append(oldIDs, int(oldID))
	}
	sort.Ints(oldIDs)
	for _, rawOldID := range oldIDs {
		oldID := int32(rawOldID)
		key := source[oldID]
		if id, ok := existing[key]; ok {
			result[oldID] = id
			continue
		}
		*next = *next + 1
		result[oldID] = *next
		existing[key] = *next
	}
	return result
}

func remapValue(old int32, values map[int32]int32) (int32, error) {
	if old == 0 {
		return 0, nil
	}
	value, ok := values[old]
	if !ok {
		return 0, fmt.Errorf("wire id %d has no model remapping", old)
	}
	return value, nil
}

func remapClass(message protoreflect.Message, field protoreflect.FieldDescriptor) mapName {
	name := string(field.Name())
	if name == "id" {
		switch message.Descriptor().Name() {
		case "ProtoTypeDefinition":
			return typeMap
		case "ProtoPackage":
			return packageMap
		case "ProtoNamedType":
			return namedMap
		case "ProtoFunction":
			return functionMap
		case "ProtoGlobal":
			return globalMap
		case "ProtoConst":
			return constMap
		}
	}
	if name == "named_type_id" || name == "embedded_interface_ids" {
		return namedMap
	}
	if name == "package_id" || name == "import_ids" {
		return packageMap
	}
	if name == "fn_id" || name == "method_ids" || name == "pointer_method_ids" ||
		strings.Contains(name, "function_id") {
		return functionMap
	}
	if name == "global_id" {
		return globalMap
	}
	if strings.HasSuffix(name, "type_id") || strings.HasSuffix(name, "type_ids") {
		return typeMap
	}
	return noMap
}

type mapName uint8

const (
	noMap mapName = iota
	typeMap
	packageMap
	functionMap
	namedMap
	globalMap
	constMap
)

func (r *idRemap) values(name mapName) map[int32]int32 {
	switch name {
	case typeMap:
		return r.types
	case packageMap:
		return r.packages
	case functionMap:
		return r.functions
	case namedMap:
		return r.named
	case globalMap:
		return r.globals
	case constMap:
		return r.consts
	default:
		return nil
	}
}

func remapMessageIDs(message protoreflect.Message, remap *idRemap) error {
	var result error
	message.Range(func(field protoreflect.FieldDescriptor, value protoreflect.Value) bool {
		if field.IsList() {
			list := value.List()
			if field.Kind() == protoreflect.MessageKind {
				for idx := 0; idx < list.Len(); idx++ {
					if err := remapMessageIDs(list.Get(idx).Message(), remap); err != nil {
						result = err
						return false
					}
				}
				return true
			}
			class := remapClass(message, field)
			if class == noMap {
				return true
			}
			for idx := 0; idx < list.Len(); idx++ {
				mapped, err := remapValue(int32(list.Get(idx).Int()), remap.values(class))
				if err != nil {
					result = fmt.Errorf("remapping %s.%s: %w", message.Descriptor().Name(), field.Name(), err)
					return false
				}
				list.Set(idx, protoreflect.ValueOfInt32(mapped))
			}
			return true
		}
		if field.Kind() == protoreflect.MessageKind {
			if err := remapMessageIDs(value.Message(), remap); err != nil {
				result = err
				return false
			}
			return true
		}
		class := remapClass(message, field)
		if class == noMap {
			return true
		}
		mapped, err := remapValue(int32(value.Int()), remap.values(class))
		if err != nil {
			result = fmt.Errorf("remapping %s.%s: %w", message.Descriptor().Name(), field.Name(), err)
			return false
		}
		message.Set(field, protoreflect.ValueOfInt32(mapped))
		return true
	})
	return result
}

type modelStructFieldPlan struct {
	targetNamedID int32
	indexByModel  map[int32]int32
	extraFields   []*pb.ProtoFieldDecl
}

func planModelStructFields(
	baseIndex, modelIndex *protoIndex,
	norm *modelPathNormalizer,
) (map[int32]*modelStructFieldPlan, error) {
	plans := make(map[int32]*modelStructFieldPlan)
	baseNamed := reverseNamedKeys(baseIndex)

	for _, pkg := range modelIndex.program.Packages {
		if _, owned := norm.replacements[pkg.ImportPath]; !owned {
			continue
		}
		for _, modelNamed := range pkg.NamedTypes {
			targetID, exists := baseNamed[modelIndex.namedKey[modelNamed.Id]]
			if !exists {
				continue
			}
			targetNamed := baseIndex.named[targetID]
			if modelNamed.Kind != pb.ProtoNamedTypeKind_NAMED_TYPE_STRUCT {
				continue
			}
			if targetNamed.Kind != pb.ProtoNamedTypeKind_NAMED_TYPE_STRUCT {
				return nil, fmt.Errorf(
					"go model type %s is a struct, but its target is not a struct",
					modelIndex.namedKey[modelNamed.Id],
				)
			}

			targetFields := make(map[string]*pb.ProtoFieldDecl, len(targetNamed.Fields))
			var nextIndex int32
			for _, field := range targetNamed.Fields {
				targetFields[field.Name] = field
				nextIndex = max(nextIndex, field.Index+1)
			}

			plan := &modelStructFieldPlan{
				targetNamedID: targetID,
				indexByModel:  make(map[int32]int32, len(modelNamed.Fields)),
			}
			for _, modelField := range modelNamed.Fields {
				modelIndexValue := modelField.Index
				if targetField := targetFields[modelField.Name]; targetField != nil {
					if modelIndex.typeKey(modelField.TypeId) != baseIndex.typeKey(targetField.TypeId) {
						return nil, fmt.Errorf(
							"go model field %s.%s has a different type from its target",
							modelIndex.namedKey[modelNamed.Id], modelField.Name,
						)
					}
					plan.indexByModel[modelIndexValue] = targetField.Index
					modelField.Index = targetField.Index
					continue
				}

				plan.indexByModel[modelIndexValue] = nextIndex
				modelField.Index = nextIndex
				plan.extraFields = append(plan.extraFields, modelField)
				nextIndex++
			}

			if underlying := modelIndex.types[modelNamed.UnderlyingTypeId].GetStructType(); underlying != nil {
				for _, field := range underlying.Fields {
					if targetIndex, exists := plan.indexByModel[field.Index]; exists {
						field.Index = targetIndex
					}
				}
			}
			plans[modelNamed.Id] = plan
		}
	}

	return plans, nil
}

func modelNamedTypeForFieldAccess(
	index *protoIndex,
	underlyingToNamed map[int32]int32,
	typeID int32,
	active map[int32]bool,
) int32 {
	if typeID == 0 || active[typeID] {
		return 0
	}
	active[typeID] = true
	defer delete(active, typeID)

	td := index.types[typeID]
	if td == nil {
		return 0
	}
	if pointer := td.GetPointer(); pointer != nil {
		return modelNamedTypeForFieldAccess(index, underlyingToNamed, pointer.ElemTypeId, active)
	}
	if named := td.GetNamedRef(); named != nil {
		return named.NamedTypeId
	}
	if td.GetStructType() != nil {
		return underlyingToNamed[typeID]
	}
	return 0
}

func remapModelStructFieldAccesses(
	model *pb.ProtoProgram,
	modelIndex *protoIndex,
	plans map[int32]*modelStructFieldPlan,
) error {
	underlyingToNamed := make(map[int32]int32, len(modelIndex.named))
	for namedID, named := range modelIndex.named {
		underlyingToNamed[named.UnderlyingTypeId] = namedID
	}

	remapAccess := func(typeID, fieldIndex int32, fieldName string) (int32, error) {
		namedID := modelNamedTypeForFieldAccess(
			modelIndex, underlyingToNamed, typeID, make(map[int32]bool),
		)
		plan := plans[namedID]
		if plan == nil {
			return fieldIndex, nil
		}
		targetIndex, exists := plan.indexByModel[fieldIndex]
		if !exists {
			return 0, fmt.Errorf(
				"go model field access %s[%d] has no target field mapping",
				fieldName, fieldIndex,
			)
		}
		return targetIndex, nil
	}

	for _, body := range model.FunctionBodies {
		for _, block := range body.Blocks {
			for _, instruction := range block.Instructions {
				if access := instruction.GetFieldAddr(); access != nil {
					fieldIndex, err := remapAccess(access.X.TypeId, access.FieldIndex, access.FieldName)
					if err != nil {
						return err
					}
					access.FieldIndex = fieldIndex
				}
				if access := instruction.GetField(); access != nil {
					fieldIndex, err := remapAccess(access.X.TypeId, access.FieldIndex, access.FieldName)
					if err != nil {
						return err
					}
					access.FieldIndex = fieldIndex
				}
			}
		}
	}
	return nil
}

func mergeModelStructFields(
	base *pb.ProtoProgram,
	baseIndex *protoIndex,
	plans map[int32]*modelStructFieldPlan,
	counters *idCounters,
) error {
	modelNamedIDs := make([]int, 0, len(plans))
	for modelNamedID := range plans {
		modelNamedIDs = append(modelNamedIDs, int(modelNamedID))
	}
	sort.Ints(modelNamedIDs)

	for _, rawModelNamedID := range modelNamedIDs {
		plan := plans[int32(rawModelNamedID)]
		if len(plan.extraFields) == 0 {
			continue
		}
		targetNamed := baseIndex.named[plan.targetNamedID]
		if targetNamed == nil {
			return fmt.Errorf("go model target named type %d is missing", plan.targetNamedID)
		}
		underlying := baseIndex.types[targetNamed.UnderlyingTypeId].GetStructType()
		if underlying == nil {
			return fmt.Errorf("go model target type %s has no struct body", targetNamed.FullName)
		}

		combinedFields := make([]*pb.ProtoStructField, 0, len(underlying.Fields)+len(plan.extraFields))
		for _, field := range underlying.Fields {
			combinedFields = append(combinedFields, proto.Clone(field).(*pb.ProtoStructField))
		}
		for _, field := range plan.extraFields {
			targetNamed.Fields = append(targetNamed.Fields, proto.Clone(field).(*pb.ProtoFieldDecl))
			combinedFields = append(combinedFields, &pb.ProtoStructField{
				Name:     field.Name,
				TypeId:   field.TypeId,
				Index:    field.Index,
				Embedded: field.Embedded,
				Exported: field.Exported,
				Tag:      field.Tag,
			})
		}

		counters.typeID++
		combined := &pb.ProtoTypeDefinition{
			Id: counters.typeID,
			Type: &pb.ProtoTypeDefinition_StructType{StructType: &pb.ProtoStructType{
				Fields: combinedFields,
			}},
		}
		base.Types = append(base.Types, combined)
		baseIndex.types[combined.Id] = combined
		targetNamed.UnderlyingTypeId = combined.Id
	}
	return nil
}

func mergeGoModel(base *pb.ProtoProgram, rawModel *pb.ProtoProgram, state *modelMergeState, source string) error {
	norm, err := modelNormalizer(rawModel)
	if err != nil {
		return fmt.Errorf("invalid Go model %s: %w", source, err)
	}
	for target := range norm.targets {
		if previous, exists := state.modeledPackages[target]; exists {
			return fmt.Errorf("Go package %q is modeled more than once (%s and %s)", target, previous, source)
		}
		state.modeledPackages[target] = source
	}

	model := proto.Clone(rawModel).(*pb.ProtoProgram)
	baseIndex := indexProto(base, nil)
	modelIndex := indexProto(model, norm)
	fieldPlans, err := planModelStructFields(baseIndex, modelIndex, norm)
	if err != nil {
		return fmt.Errorf("invalid Go model %s: %w", source, err)
	}
	if err := remapModelStructFieldAccesses(model, modelIndex, fieldPlans); err != nil {
		return fmt.Errorf("invalid Go model %s: %w", source, err)
	}
	modelOwnedOldPackages := make(map[int32]bool)
	modelInitOldFunctions := make(map[int32]bool)
	for _, pkg := range model.Packages {
		if _, ok := norm.replacements[pkg.ImportPath]; !ok {
			continue
		}
		modelOwnedOldPackages[pkg.Id] = true
		for _, fn := range pkg.Functions {
			if fn.Name == "init" || strings.HasPrefix(fn.Name, "init#") {
				modelInitOldFunctions[fn.Id] = true
			}
		}
	}
	// Closures declared inside an initializer are initializer implementation
	// details too. Exclude the whole descendant tree, not just the synthetic
	// package initializer and the compiler-numbered init#N roots.
	for changed := true; changed; {
		changed = false
		for _, pkg := range model.Packages {
			if !modelOwnedOldPackages[pkg.Id] {
				continue
			}
			for _, fn := range pkg.Functions {
				if !modelInitOldFunctions[fn.Id] && modelInitOldFunctions[fn.ParentFunctionId] {
					modelInitOldFunctions[fn.Id] = true
					changed = true
				}
			}
		}
	}
	basePackages := reversePackageKeys(baseIndex)
	for _, pkg := range model.Packages {
		target, owned := norm.replacements[pkg.ImportPath]
		if !owned {
			continue
		}
		basePackageID, targetExists := basePackages[target]
		if targetExists && pkg.Name != baseIndex.packages[basePackageID].Name {
			return fmt.Errorf(
				"Go model package %q has package name %q, want %q (the modeled package name)",
				pkg.ImportPath, pkg.Name, baseIndex.packages[basePackageID].Name,
			)
		}
	}
	counters := countersFor(base)
	remap := newIDRemap()

	modelPackageKeys := modelIndex.packageKey
	modelNamedKeys := modelIndex.namedKey
	modelTypeKeys := typeKeysByID(modelIndex)
	modelFunctionKeys := functionKeysByID(modelIndex)
	// Anonymous functions are implementation details of the modeled body, not
	// declarations to match against similarly numbered closures in the target
	// body. Give them fresh identities even when their generated full names
	// happen to be equal.
	for id, fn := range modelIndex.funcs {
		if fn.ParentFunctionId != 0 {
			modelFunctionKeys[id] += fmt.Sprintf("#opentaint-model-anonymous:%d", id)
		}
	}
	modelGlobalKeys := globalKeysByID(modelIndex)
	modelConstKeys := constKeysByID(modelIndex)

	remap.packages = assignIDs(modelPackageKeys, reversePackageKeys(baseIndex), &counters.packageID)
	remap.named = assignIDs(modelNamedKeys, reverseNamedKeys(baseIndex), &counters.namedID)
	remap.types = assignIDs(modelTypeKeys, reverseTypeKeys(baseIndex), &counters.typeID)
	remap.functions = assignIDs(modelFunctionKeys, reverseFunctionKeys(baseIndex), &counters.functionID)
	remap.globals = assignIDs(modelGlobalKeys, reverseGlobalKeys(baseIndex), &counters.globalID)
	remap.consts = assignIDs(modelConstKeys, reverseConstKeys(baseIndex), &counters.constID)

	// A body is attached to the target declaration, so a same-named model
	// must have exactly the target signature.  Go has no overloads that could
	// otherwise disambiguate the mismatch.
	baseFunctions := reverseFunctionKeys(baseIndex)
	for modelID, key := range modelFunctionKeys {
		baseID, exists := baseFunctions[key]
		if !exists {
			continue
		}
		modelFn := modelIndex.funcs[modelID]
		if modelFn == nil || modelFn.ParentFunctionId != 0 || modelInitOldFunctions[modelID] ||
			!modelOwnedPackage(modelIndex, norm, modelFn.PackageId) {
			continue
		}
		baseFn := baseIndex.funcs[baseID]
		if modelIndex.typeKey(modelFn.SignatureTypeId) != baseIndex.typeKey(baseFn.SignatureTypeId) ||
			modelFn.IsMethod != baseFn.IsMethod || modelFn.IsPointerReceiver != baseFn.IsPointerReceiver {
			return fmt.Errorf("Go model function %s has a different signature from its target", key)
		}
	}

	for _, pkg := range model.Packages {
		if !modelOwnedOldPackages[pkg.Id] {
			continue
		}
		pkg.InitFunctionId = 0
		pkg.ImportPath = norm.path(pkg.ImportPath)
		pkg.IsDependency = true
		pkg.IsStdlib = false
	}
	for _, pkg := range model.Packages {
		for _, named := range pkg.NamedTypes {
			named.FullName = norm.symbol(named.FullName)
		}
		for _, fn := range pkg.Functions {
			fn.FullName = norm.symbol(fn.FullName)
		}
		for _, global := range pkg.Globals {
			global.FullName = norm.symbol(global.FullName)
		}
		for _, c := range pkg.Constants {
			c.FullName = norm.symbol(c.FullName)
		}
	}

	modelOwnedPackages := make(map[int32]bool, len(modelOwnedOldPackages))
	for id := range modelOwnedOldPackages {
		modelOwnedPackages[remap.packages[id]] = true
	}
	modelInitFunctions := make(map[int32]bool, len(modelInitOldFunctions))
	for id := range modelInitOldFunctions {
		modelInitFunctions[remap.functions[id]] = true
	}

	if err := remapMessageIDs(model.ProtoReflect(), remap); err != nil {
		return fmt.Errorf("merging Go model %s: %w", source, err)
	}

	baseIndex = indexProto(base, nil)
	if err := mergeModelStructFields(base, baseIndex, fieldPlans, &counters); err != nil {
		return fmt.Errorf("merging Go model %s: %w", source, err)
	}
	for _, td := range model.Types {
		if baseIndex.types[td.Id] == nil {
			base.Types = append(base.Types, td)
			baseIndex.types[td.Id] = td
		}
	}

	for _, modelPkg := range model.Packages {
		basePkg := baseIndex.packages[modelPkg.Id]
		if basePkg == nil {
			if modelOwnedPackages[modelPkg.Id] {
				functions := modelPkg.Functions[:0]
				for _, fn := range modelPkg.Functions {
					if modelInitFunctions[fn.Id] {
						continue
					}
					fn.IsSynthetic = true
					fn.SyntheticKind = "opentaint model support"
					functions = append(functions, fn)
				}
				modelPkg.Functions = functions
			}
			base.Packages = append(base.Packages, modelPkg)
			baseIndex.packages[modelPkg.Id] = modelPkg
			basePkg = modelPkg
			continue
		}

		basePkg.ImportIds = appendUniqueIDs(basePkg.ImportIds, modelPkg.ImportIds...)
		existingNamed := namedByID(basePkg.NamedTypes)
		for _, named := range modelPkg.NamedTypes {
			if current := existingNamed[named.Id]; current != nil {
				current.MethodIds = appendUniqueIDs(current.MethodIds, named.MethodIds...)
				current.PointerMethodIds = appendUniqueIDs(current.PointerMethodIds, named.PointerMethodIds...)
				continue
			}
			basePkg.NamedTypes = append(basePkg.NamedTypes, named)
			existingNamed[named.Id] = named
		}

		existingFunctions := functionsByID(basePkg.Functions)
		for _, fn := range modelPkg.Functions {
			if modelInitFunctions[fn.Id] {
				continue
			}
			if current := existingFunctions[fn.Id]; current != nil {
				if modelOwnedPackages[modelPkg.Id] {
					current.AnonFunctionIds = fn.AnonFunctionIds
				}
				continue
			}
			if modelOwnedPackages[modelPkg.Id] {
				fn.IsSynthetic = true
				fn.SyntheticKind = "opentaint model support"
			}
			basePkg.Functions = append(basePkg.Functions, fn)
			existingFunctions[fn.Id] = fn
		}
		mergeGlobals(&basePkg.Globals, modelPkg.Globals)
		mergeConsts(&basePkg.Constants, modelPkg.Constants)
	}

	baseIndex = indexProto(base, nil)
	for _, body := range model.FunctionBodies {
		if modelInitFunctions[body.FunctionId] {
			continue
		}
		fn := baseIndex.funcs[body.FunctionId]
		if fn == nil || !modelOwnedPackages[fn.PackageId] {
			continue
		}
		if previous, exists := state.modeledFunctions[body.FunctionId]; exists {
			return fmt.Errorf("Go function %q is modeled more than once (%s and %s)", fn.FullName, previous, source)
		}
		state.modeledFunctions[body.FunctionId] = source
		base.FunctionBodies = replaceFunctionBody(base.FunctionBodies, body)
		fn.HasBody = true
	}

	return nil
}

func modelOwnedPackage(index *protoIndex, norm *modelPathNormalizer, packageID int32) bool {
	pkg := index.packages[packageID]
	if pkg == nil {
		return false
	}
	_, ok := norm.replacements[pkg.ImportPath]
	return ok
}

func appendUniqueIDs(dst []int32, values ...int32) []int32 {
	seen := make(map[int32]bool, len(dst)+len(values))
	for _, value := range dst {
		seen[value] = true
	}
	for _, value := range values {
		if value != 0 && !seen[value] {
			dst = append(dst, value)
			seen[value] = true
		}
	}
	return dst
}

func namedByID(values []*pb.ProtoNamedType) map[int32]*pb.ProtoNamedType {
	result := make(map[int32]*pb.ProtoNamedType, len(values))
	for _, value := range values {
		result[value.Id] = value
	}
	return result
}

func functionsByID(values []*pb.ProtoFunction) map[int32]*pb.ProtoFunction {
	result := make(map[int32]*pb.ProtoFunction, len(values))
	for _, value := range values {
		result[value.Id] = value
	}
	return result
}

func mergeGlobals(dst *[]*pb.ProtoGlobal, values []*pb.ProtoGlobal) {
	seen := make(map[int32]bool, len(*dst))
	for _, value := range *dst {
		seen[value.Id] = true
	}
	for _, value := range values {
		if !seen[value.Id] {
			*dst = append(*dst, value)
			seen[value.Id] = true
		}
	}
}

func mergeConsts(dst *[]*pb.ProtoConst, values []*pb.ProtoConst) {
	seen := make(map[int32]bool, len(*dst))
	for _, value := range *dst {
		seen[value.Id] = true
	}
	for _, value := range values {
		if !seen[value.Id] {
			*dst = append(*dst, value)
			seen[value.Id] = true
		}
	}
}

func replaceFunctionBody(values []*pb.ProtoFunctionBody, replacement *pb.ProtoFunctionBody) []*pb.ProtoFunctionBody {
	for idx, body := range values {
		if body.FunctionId == replacement.FunctionId {
			values[idx] = replacement
			return values
		}
	}
	return append(values, replacement)
}
