package util

import "slices"

func Source() string { return "tainted" }
func Sink(s string)  { _ = s }

// Coverage intent for the folded slices.* passthroughs. PARKED (@Disabled): the
// stdlib slices.* functions are generic (e.g. Clone[S ~[]E, E any]) and the config
// key {package: slices, name: Clone} does not match the generic-instantiated call
// in any path -- so these entries were already INERT before the fold (verified:
// slices.Clone element flow is not detected even with the pre-fold [*] stars, in
// both the querylang harness and production). Removing their stars is therefore
// neutral. These flows are kept as documentation and light up if generic-function
// config matching is ever added to the engine.
func Positive_slices_clone() {
	s := []string{Source()}
	c := slices.Clone(s)
	Sink(c[0])
}

func Positive_slices_compact() {
	s := []string{Source(), Source()}
	c := slices.Compact(s)
	Sink(c[0])
}

func Positive_slices_delete() {
	s := []string{Source(), "a"}
	c := slices.Delete(s, 1, 2)
	Sink(c[0])
}
