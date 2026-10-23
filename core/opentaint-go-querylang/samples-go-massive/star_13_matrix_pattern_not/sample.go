package util

// Starred PATTERN-NOT sink, 5+ interprocedural depth x 5+ field depth combined (Go port of
// StarMatrixPatternNot). The sink is `Emit($*Y, $MODE)` with
// `pattern-not: Emit($*Y, "safe")` — the starred metavar occurrence appears in BOTH the
// pattern and the pattern-not (the constraint solver keeps $Y and $*Y distinct, so the forms
// must agree). A starred source five calls deep taints a whole L0; the object travels five
// hops and is emitted five calls deep — flagged in "html" mode, excluded by the pattern-not
// in "safe" mode.
type L0 struct{ F L1 }
type L1 struct{ F L2 }
type L2 struct{ F L3 }
type L3 struct{ F L4 }
type L4 struct{ V string }

func Source() L0 { return L0{} }

func Emit(o L0, mode string) { _, _ = o, mode }

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

// Two sink chains five calls deep: one emits in a flagged mode, one in the excluded mode.
func k1(o L0) { k2(o) }
func k2(o L0) { k3(o) }
func k3(o L0) { k4(o) }
func k4(o L0) { k5(o) }
func k5(o L0) { Emit(o, "html") } // matches the sink, depth 5

func j1(o L0) { j2(o) }
func j2(o L0) { j3(o) }
func j3(o L0) { j4(o) }
func j4(o L0) { j5(o) }
func j5(o L0) { Emit(o, "safe") } // excluded by pattern-not, depth 5

// Positive_emit_html: tainted object emitted in a non-excluded mode.
func Positive_emit_html() {
	o := src5()
	k1(p5(p4(p3(p2(p1(o))))))
}

// Negative_emit_safe: same tainted object, but the emit call matches the pattern-not.
func Negative_emit_safe() {
	o := src5()
	j1(p5(p4(p3(p2(p1(o))))))
}
