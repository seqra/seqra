package util

// Starred SANITIZER, 5+ interprocedural depth x 5+ field depth combined (Go port of
// StarMatrixSanitizer). A starred source five calls deep taints a whole L0. On the sanitized
// path the object goes through `Sanitize()` — a HELPER whose body calls the starred-clean
// `Clean()` (the wrapper shape behind the OWASP escapeHtml FPs, i.e. the deep-mark-exclusion
// fix's sample-level regression test). Afterwards five hops unwrap one field level each and
// the scalar travels five calls down to the sink; the whole-object clean must have removed
// the any-field taint at every depth.
type L0 struct{ F L1 }
type L1 struct{ F L2 }
type L2 struct{ F L3 }
type L3 struct{ F L4 }
type L4 struct{ V string }

func Source() L0 { return L0{} }

func Clean(o L0) L0 { return o }

func Sink(s string) { _ = s }

// Source five calls deep.
func src5() L0 { return src4() }
func src4() L0 { return src3() }
func src3() L0 { return src2() }
func src2() L0 { return src1() }
func src1() L0 { o := Source(); return o } // $*X = Source() matches HERE, depth 5

// The starred clean sits INSIDE a wrapper: its whole-object effect must survive the
// wrapper's interprocedural summary (deep mark exclusions).
func Sanitize(o L0) L0 { return Clean(o) }

// Five hops, each unwrapping exactly one field level.
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

// Positive_unsanitized_deep: the unsanitized path flags.
func Positive_unsanitized_deep() {
	o := src5()
	s := u5(u4(u3(u2(u1(o)))))
	k1(s)
}

// Negative_sanitized_deep: the wrapped whole-object clean clears the taint at every field
// depth.
func Negative_sanitized_deep() {
	o := src5()
	c := Sanitize(o)
	s := u5(u4(u3(u2(u1(c)))))
	k1(s)
}
