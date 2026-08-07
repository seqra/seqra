package util

// Starred SINK, 5+ interprocedural depth x 5+ field depth combined (Go port of
// StarMatrixSink). A starred source taints the INNERMOST object (L4) five calls deep;
// five hops then each WRAP it one level deeper (L4->L3->..->L0), and the outermost object
// travels five calls down a sink chain to `Sink_L0($*Y)` — the starred sink must observe
// the whole-object taint buried five field levels down the wrapped object.
type L0 struct{ F L1 }
type L1 struct{ F L2 }
type L2 struct{ F L3 }
type L3 struct{ F L4 }
type L4 struct{ V string }

func Source() L4 { return L4{} }

func Sink_L0(o L0) { _ = o }

// Source five calls deep: the starred source statement is inside src1.
func src5() L4 { return src4() }
func src4() L4 { return src3() }
func src3() L4 { return src2() }
func src2() L4 { return src1() }
func src1() L4 { o := Source(); return o } // $*X = Source() matches HERE, depth 5

// Five hops, each WRAPPING one field level (hide direction).
func w1(o L4) L3 { var n L3; n.F = o; return n }
func w2(o L3) L2 { var n L2; n.F = o; return n }
func w3(o L2) L1 { var n L1; n.F = o; return n }
func w4(o L1) L0 { var n L0; n.F = o; return n }
func w5(o L0) L0 { return o }

// Sink five calls deep.
func k1(o L0) { k2(o) }
func k2(o L0) { k3(o) }
func k3(o L0) { k4(o) }
func k4(o L0) { k5(o) }
func k5(o L0) { Sink_L0(o) } // Sink_L0($*Y) matches HERE, depth 5

// Positive_wrapped_deep: the tainted L4 is wrapped five levels deep; the starred sink
// observes it.
func Positive_wrapped_deep() {
	t := src5()
	o := w5(w4(w3(w2(w1(t)))))
	k1(o)
}

// Negative_clean_wrapped: an untainted L4 wrapped and threaded through the identical chains.
func Negative_clean_wrapped() {
	var t L4
	t.V = "safe"
	o := w5(w4(w3(w2(w1(t)))))
	k1(o)
}
