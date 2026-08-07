package util

// The starred whole-object source taints every nested field at every depth; a 5-level
// field read must therefore be tainted too.
type L0 struct{ F L1 }
type L1 struct{ F L2 }
type L2 struct{ F L3 }
type L3 struct{ F L4 }
type L4 struct{ V string }

func Source() L0 { return L0{} }

func Sink(s string) { _ = s }

// Positive_deep_field_read: the starred source ($*X = Source()) taints the whole object AND
// all nested fields; the depth-5 field read o.F.F.F.F.V then reaches the plain sink.
func Positive_deep_field_read() {
	o := Source()
	Sink(o.F.F.F.F.V)
}

// Negative_untainted: the object is built from constants, so no field is tainted.
func Negative_untainted() {
	var o L0
	o.F.F.F.F.V = "safe"
	Sink(o.F.F.F.F.V)
}
