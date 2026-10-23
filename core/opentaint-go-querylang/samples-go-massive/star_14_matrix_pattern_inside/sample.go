package util

// Starred sink gated by PATTERN-INSIDE, 5+ interprocedural depth x 5+ field depth combined
// (Go port of StarMatrixPatternInside). The sink `$R.Consume($*Y)` only counts when the
// receiver comes from `OpenSink()` in the same function (pattern-inside). A starred source
// five calls deep taints a whole L0; the object travels five hops; the Consume call sits five
// calls deep. The gated function uses OpenSink() (flagged); the ungated one obtains its
// receiver elsewhere (not a sink at all).
type Out struct{}

func (r Out) Consume(o L0) { _ = o }

type L0 struct{ F L1 }
type L1 struct{ F L2 }
type L2 struct{ F L3 }
type L3 struct{ F L4 }
type L4 struct{ V string }

func Source() L0 { return L0{} }

func OpenSink() Out { return Out{} }

func PlainOut() Out { return Out{} }

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

// Sink chain five calls deep, ending in the pattern-inside-gated Consume.
func k1(o L0) { k2(o) }
func k2(o L0) { k3(o) }
func k3(o L0) { k4(o) }
func k4(o L0) { k5(o) }
func k5(o L0) {
	r := OpenSink() // pattern-inside context
	r.Consume(o)    // starred sink matches HERE, depth 5
}

// Same-depth chain whose Consume receiver does NOT come from OpenSink().
func j1(o L0) { j2(o) }
func j2(o L0) { j3(o) }
func j3(o L0) { j4(o) }
func j4(o L0) { j5(o) }
func j5(o L0) {
	r := PlainOut() // no pattern-inside context
	r.Consume(o)
}

// Positive_gated_consume: tainted object consumed inside the gated context.
func Positive_gated_consume() {
	o := src5()
	k1(p5(p4(p3(p2(p1(o))))))
}

// Negative_ungated_consume: same tainted object, but the Consume call lacks the
// pattern-inside context.
func Negative_ungated_consume() {
	o := src5()
	j1(p5(p4(p3(p2(p1(o))))))
}
