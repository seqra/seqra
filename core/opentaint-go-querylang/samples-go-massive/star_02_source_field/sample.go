package util

// Data is the whole object tainted by the starred source; every nested field
// inherits the taint, so a later field read is tainted too.
type Data struct {
	Field string
}

func Source() Data { return Data{Field: "tainted"} }

func Sink(s string) { _ = s }

// Positive_field_read: the starred source ($*X = Source()) taints the whole object
// AND all its fields; the field read d.Field then reaches the plain sink.
func Positive_field_read() {
	d := Source()
	Sink(d.Field)
}

// Negative_untainted: the object is built from a constant, so no field is tainted.
func Negative_untainted() {
	var d Data
	d.Field = "safe"
	Sink(d.Field)
}
