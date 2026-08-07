package util

// Box carries the tainted field. The starred sanitizer Clean($*C) must clear the
// taint on the whole object INCLUDING the nested field, so a later field read is clean.
type Box struct {
	Value string
}

func Source() string { return "tainted" }

// Clean is the $*C sanitizer: it clears the argument object and all of its fields.
func Clean(b Box) Box { return b }

func Sink(s string) { _ = s }

// Positive_unsanitized: field taint reaches the sink with no sanitizer in between.
func Positive_unsanitized() {
	var b Box
	b.Value = Source()
	Sink(b.Value)
}

// Negative_sanitized: the starred sanitizer sits between source and sink; if $*C truly
// clears the concrete nested-field taint, the field read must be clean and nothing reports.
func Negative_sanitized() {
	var b Box
	b.Value = Source()
	cleaned := Clean(b)
	Sink(cleaned.Value)
}
