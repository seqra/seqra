package util

// Starred sink guarded by PATTERN-NOT-INSIDE, 5+ interprocedural depth x 5+ field depth
// combined (Go port of StarMatrixPatternNotInside). The sink `Use($*Y)` sits in a
// `pattern-inside` context that INTRODUCES the guard receiver (`$G = NewChecker(); ...`),
// and `pattern-not-inside: $G.Check($*Y); ...` suppresses it — every not-inside metavar must
// be introduced and wired by the pattern-inside/sink patterns (a not-inside with unbound
// metavars is dropped during automata-to-taint-rule conversion). A starred source five calls
// deep taints a whole L0; the object travels five hops; the Use call sits five calls deep —
// flagged in the unguarded function, suppressed in the guarded one.
type Checker struct{}

func (c Checker) Check(o L0) { _ = o }

type L0 struct{ F L1 }
type L1 struct{ F L2 }
type L2 struct{ F L3 }
type L3 struct{ F L4 }
type L4 struct{ V string }

func Source() L0 { return L0{} }

func NewChecker() Checker { return Checker{} }

func Use(o L0) { _ = o }

// Source five calls deep.
func src5() L0 { return src4() }
func src4() L0 { return src3() }
func src3() L0 { return src2() }
func src2() L0 { return src1() }
func src1() L0 { o := Source(); return o } // $*X = Source() matches HERE, depth 5

// Five object pass-hops.
func p1(o L0) L0 { return o }
func p2(o L0) L0 { return o }
func p3(o L0) L0 { return o }
func p4(o L0) L0 { return o }
func p5(o L0) L0 { return o }

// Unguarded sink chain five calls deep.
func k1(o L0) { k2(o) }
func k2(o L0) { k3(o) }
func k3(o L0) { k4(o) }
func k4(o L0) { k5(o) }
func k5(o L0) {
	g := NewChecker() // pattern-inside context (binds $G), no Check() -> flagged
	_ = g
	Use(o) // starred sink matches HERE, depth 5
}

// Guarded sink chain five calls deep: Check() precedes the Use in the same function.
func j1(o L0) { j2(o) }
func j2(o L0) { j3(o) }
func j3(o L0) { j4(o) }
func j4(o L0) { j5(o) }
func j5(o L0) {
	g := NewChecker() // pattern-inside context (binds $G)
	g.Check(o)        // pattern-not-inside: $G.Check($*Y) precedes -> suppressed
	Use(o)
}

// Positive_unguarded_use: tainted object used without the guard.
func Positive_unguarded_use() {
	o := src5()
	k1(p5(p4(p3(p2(p1(o))))))
}

// Negative_guarded_use: same tainted object, but the Use is preceded by Check().
func Negative_guarded_use() {
	o := src5()
	j1(p5(p4(p3(p2(p1(o))))))
}
