package util

// Starred PROPAGATOR — BOTH occurrences starred (`$*T = Pass($*F)`) — at 5+ interprocedural
// depth x 5+ field depth (Go port of StarMatrixPropagator). A starred source five calls deep
// taints a whole L0; the object travels five pass-hops to the propagator call, whose starred
// FROM observes the any-field taint of the whole argument and whose starred TO assigns
// whole-object taint to the fresh M0 result. The M0 is then unwrapped ONE field level per hop
// across five calls (M0->..->string) — only possible if the TO really carries any-field taint
// — and the scalar travels five calls down a sink chain to a plain sink.
type L0 struct{ F L1 }
type L1 struct{ F L2 }
type L2 struct{ F L3 }
type L3 struct{ F L4 }
type L4 struct{ V string }

type M0 struct{ F M1 }
type M1 struct{ F M2 }
type M2 struct{ F M3 }
type M3 struct{ F M4 }
type M4 struct{ V string }

func Source() L0 { return L0{} }

func Pass(o L0) M0 { _ = o; return M0{} }

func Sink(s string) { _ = s }

// Source five calls deep.
func src5() L0 { return src4() }
func src4() L0 { return src3() }
func src3() L0 { return src2() }
func src2() L0 { return src1() }
func src1() L0 { o := Source(); return o } // $*X = Source() matches HERE, depth 5

// Five object pass-hops before the propagator.
func p1(o L0) L0 { return o }
func p2(o L0) L0 { return o }
func p3(o L0) L0 { return o }
func p4(o L0) L0 { return o }
func p5(o L0) L0 { return o }

// Five hops, each unwrapping one field level of the PROPAGATED object: taint reaches the
// scalar only if the starred TO assigned any-field taint to the M0.
func u1(o M0) M1     { return o.F }
func u2(o M1) M2     { return o.F }
func u3(o M2) M3     { return o.F }
func u4(o M3) M4     { return o.F }
func u5(o M4) string { return o.V }

// Sink five calls deep.
func k1(s string) { k2(s) }
func k2(s string) { k3(s) }
func k3(s string) { k4(s) }
func k4(s string) { k5(s) }
func k5(s string) { Sink(s) } // Sink() called HERE, depth 5

// Positive_propagated_deep: deep source -> 5 hops -> starred propagator -> per-hop unwrap
// -> deep sink.
func Positive_propagated_deep() {
	o := src5()
	o5 := p5(p4(p3(p2(p1(o)))))
	t := Pass(o5) // $*T = Pass($*F): whole object in, whole object out
	s := u5(u4(u3(u2(u1(t)))))
	k1(s)
}

// Negative_clean_propagated: an untainted object through the identical propagator and chains.
func Negative_clean_propagated() {
	var o L0
	o5 := p5(p4(p3(p2(p1(o)))))
	t := Pass(o5)
	s := u5(u4(u3(u2(u1(t)))))
	k1(s)
}
