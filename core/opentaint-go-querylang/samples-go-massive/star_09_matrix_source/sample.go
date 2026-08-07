package util

// Starred SOURCE, 5+ interprocedural depth x 5+ field depth combined (Go port of
// StarMatrixSource). The source statement `$*X = Source()` sits FIVE calls deep
// (src1..src5); the tainted whole object then climbs back up and is unwrapped ONE field
// level per hop across five more calls (u1..u5, L0->..->string), and the scalar finally
// travels five calls down a sink chain (k1..k5) to a plain sink. The 5-level field taint
// is carried by the $* source's abstract any-field mark.
type L0 struct{ F L1 }
type L1 struct{ F L2 }
type L2 struct{ F L3 }
type L3 struct{ F L4 }
type L4 struct{ V string }

func Source() L0 { return L0{} }

func Sink(s string) { _ = s }

// Source five calls deep: the starred source statement is inside src1.
func src5() L0 { return src4() }
func src4() L0 { return src3() }
func src3() L0 { return src2() }
func src2() L0 { return src1() }
func src1() L0 { o := Source(); return o } // $*X = Source() matches HERE, depth 5

// Five hops, each unwrapping exactly one field level: interproc depth x field depth.
func u1(o L0) L1     { return o.F }
func u2(o L1) L2     { return o.F }
func u3(o L2) L3     { return o.F }
func u4(o L3) L4     { return o.F }
func u5(o L4) string { return o.V }

// Sink five calls deep.
func k1(s string) { k2(s) }
func k2(s string) { k3(s) }
func k3(s string) { k4(s) }
func k4(s string) { k5(s) }
func k5(s string) { Sink(s) } // Sink() called HERE, depth 5

// Positive_deep_chain: deep source -> 5x1-field unwrap hops -> deep sink.
func Positive_deep_chain() {
	o := src5()
	s := u5(u4(u3(u2(u1(o)))))
	k1(s)
}

// Negative_clean_chain: an untainted object through the identical chains.
func Negative_clean_chain() {
	var o L0
	o.F.F.F.F.V = "safe"
	s := u5(u4(u3(u2(u1(o)))))
	k1(s)
}
