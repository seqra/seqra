package util

// Both ends starred: a whole-object source ($*X = Source()) taints every nested field, and a
// whole-object sink (Sink_Inner($*Y)) observes a nested sub-object pulled out in between.
type Outer struct{ F Mid }
type Mid struct{ F Inner }
type Inner struct{ V string }

func Source() Outer { return Outer{} }

func Sink_Inner(i Inner) { _ = i }

// Positive_nested_object_to_star_sink: the whole-object source taint reaches a nested
// sub-object handed to the starred sink.
func Positive_nested_object_to_star_sink() {
	o := Source()
	inner := o.F.F
	Sink_Inner(inner)
}

// Negative_clean_nested: locally-built object, nothing tainted.
func Negative_clean_nested() {
	var o Outer
	o.F.F.V = "safe"
	Sink_Inner(o.F.F)
}
