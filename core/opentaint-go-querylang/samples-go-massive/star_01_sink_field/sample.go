package util

// Box carries a single string field; the star operator lets a whole-object sink
// observe taint that lives on a nested field rather than the object's base value.
type Box struct {
	Value string
}

func Source() string { return "tainted" }

func Sink_Box(b Box) { _ = b }

// Positive_tainted_field: a source-tainted value is written into b.Value (a nested
// field). The starred sink Sink_Box($*Y) matches the field taint on the whole object.
func Positive_tainted_field() {
	var b Box
	b.Value = Source()
	Sink_Box(b)
}

// Negative_clean_object: the field is never tainted, so the starred sink stays silent.
func Negative_clean_object() {
	var b Box
	b.Value = "safe"
	Sink_Box(b)
}
