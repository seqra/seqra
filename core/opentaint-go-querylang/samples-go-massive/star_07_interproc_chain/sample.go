package util

// Box carries a single string field. A starred whole-object source is threaded through a 5+
// hop interprocedural chain that alternately hides taint inside the object and exposes it.
type Box struct{ V string }

func Source() Box { return Box{} }

func Sink(s string) { _ = s }

// step1..step5: 5 interprocedural hops. Alternation:
//   step1 pass object -> step2 EXPOSE field to scalar -> step3 HIDE scalar in a new Box
//   -> step4 pass object -> step5 EXPOSE the field again, reaching the sink.
func step1(b Box) Box    { return b }
func step2(b Box) string { return b.V }
func step3(s string) Box { return Box{V: s} }
func step4(b Box) Box    { return b }
func step5(b Box) string { return b.V }

// Positive_alternating_chain: taint survives 5 hops of hide/expose alternation from a starred
// source ($*X = Source()).
func Positive_alternating_chain() {
	b := Source()
	b1 := step1(b)
	s2 := step2(b1)
	b3 := step3(s2)
	b4 := step4(b3)
	s5 := step5(b4)
	Sink(s5)
}

// Positive_passthrough_chain: simplest 5-hop pass-through, field exposed only at the end.
func Positive_passthrough_chain() {
	b := Source()
	b1 := step1(b)
	b2 := step1(b1)
	b3 := step1(b2)
	b4 := step4(b3)
	s := step5(b4)
	Sink(s)
}

// Negative_clean_chain: a fresh untainted Box threaded through the same chain.
func Negative_clean_chain() {
	b := Box{V: "safe"}
	b1 := step1(b)
	s2 := step2(b1)
	b3 := step3(s2)
	b4 := step4(b3)
	s5 := step5(b4)
	Sink(s5)
}
