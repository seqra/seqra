package server

import (
	"go/types"

	"golang.org/x/tools/go/ssa"
)

type idAllocator struct {
	typeIDs     map[types.Type]int32
	packageIDs  map[*ssa.Package]int32
	functionIDs map[*ssa.Function]int32
	namedIDs    map[*types.TypeName]int32
	globalIDs   map[*ssa.Global]int32
	constIDs    map[*ssa.NamedConst]int32

	nextTypeID     int32
	nextPackageID  int32
	nextFunctionID int32
	nextNamedID    int32
	nextGlobalID   int32
	nextConstID    int32
}

func newIDAllocator() *idAllocator {
	return &idAllocator{
		typeIDs:        make(map[types.Type]int32),
		packageIDs:     make(map[*ssa.Package]int32),
		functionIDs:    make(map[*ssa.Function]int32),
		namedIDs:       make(map[*types.TypeName]int32),
		globalIDs:      make(map[*ssa.Global]int32),
		constIDs:       make(map[*ssa.NamedConst]int32),
		nextTypeID:     1,
		nextPackageID:  1,
		nextFunctionID: 1,
		nextNamedID:    1,
		nextGlobalID:   1,
		nextConstID:    1,
	}
}

func (a *idAllocator) typeID(t types.Type) int32 {
	if id, ok := a.typeIDs[t]; ok {
		return id
	}
	id := a.nextTypeID
	a.nextTypeID++
	a.typeIDs[t] = id
	return id
}

func (a *idAllocator) packageID(p *ssa.Package) int32 {
	if id, ok := a.packageIDs[p]; ok {
		return id
	}
	id := a.nextPackageID
	a.nextPackageID++
	a.packageIDs[p] = id
	return id
}

func (a *idAllocator) functionID(f *ssa.Function) int32 {
	if id, ok := a.functionIDs[f]; ok {
		return id
	}
	id := a.nextFunctionID
	a.nextFunctionID++
	a.functionIDs[f] = id
	return id
}

func (a *idAllocator) namedID(n *types.TypeName) int32 {
	if id, ok := a.namedIDs[n]; ok {
		return id
	}
	id := a.nextNamedID
	a.nextNamedID++
	a.namedIDs[n] = id
	return id
}

func (a *idAllocator) globalID(g *ssa.Global) int32 {
	if id, ok := a.globalIDs[g]; ok {
		return id
	}
	id := a.nextGlobalID
	a.nextGlobalID++
	a.globalIDs[g] = id
	return id
}

func (a *idAllocator) constID(c *ssa.NamedConst) int32 {
	if id, ok := a.constIDs[c]; ok {
		return id
	}
	id := a.nextConstID
	a.nextConstID++
	a.constIDs[c] = id
	return id
}
