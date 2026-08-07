package util

// L0..L4 nest a string field 5 levels deep. The starred whole-object sink must observe
// taint that lives on a nested field.
type L0 struct {
	V0 string
	F  *L1
}
type L1 struct {
	V1 string
	F  *L2
}
type L2 struct {
	V2 string
	F  *L3
}
type L3 struct {
	V3 string
	F  *L4
}
type L4 struct{ V string }

func Source() string { return "tainted" }

func Sink_L0(b L0) { _ = b }

func build() L0 {
	return L0{F: &L1{F: &L2{F: &L3{F: &L4{}}}}}
}

// Positive_depth1: taint at field depth 1; the starred sink observes it.
func Positive_depth1() {
	o := build()
	o.V0 = Source()
	Sink_L0(o)
}

// Positive_depth5: taint hidden 5 fields deep; the starred sink must still match.
func Positive_depth5() {
	o := build()
	o.F.F.F.F.V = Source()
	Sink_L0(o)
}

// Negative_clean_object: no field ever tainted, so the starred sink stays silent.
func Negative_clean_object() {
	o := build()
	o.F.F.F.F.V = "safe"
	Sink_L0(o)
}
