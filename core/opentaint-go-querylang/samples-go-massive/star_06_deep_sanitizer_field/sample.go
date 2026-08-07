package util

// L0..L3 nest a string field 4 levels deep. The starred sanitizer Clean($*C) must clear the
// taint on the whole object INCLUDING the nested field, so a later deep field read is clean.
type L0 struct{ F *L1 }
type L1 struct{ F *L2 }
type L2 struct{ F *L3 }
type L3 struct{ V string }

func Source() string { return "tainted" }

// Clean is the $*C sanitizer: it clears the argument object and all of its nested fields.
func Clean(b L0) L0 { return b }

func Sink(s string) { _ = s }

func build() L0 {
	return L0{F: &L1{F: &L2{F: &L3{}}}}
}

// Positive_unsanitized: deep field taint reaches the sink with no sanitizer in between.
func Positive_unsanitized() {
	o := build()
	o.F.F.F.V = Source()
	Sink(o.F.F.F.V)
}

// Negative_sanitized: the starred sanitizer sits between source and sink; if $*C truly clears
// the concrete deep-field taint, the field read must be clean and nothing reports.
func Negative_sanitized() {
	o := build()
	o.F.F.F.V = Source()
	cleaned := Clean(o)
	Sink(cleaned.F.F.F.V)
}
