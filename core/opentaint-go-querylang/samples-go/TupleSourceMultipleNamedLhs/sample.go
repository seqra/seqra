package tuplesourcemultiplenamedlhs

func SourceTuple() (string, error) { return "", nil }
func Sink(value string)            {}

// Positive_tuple_slot_zero: focus should choose the first of two named LHS targets.
func Positive_tuple_slot_zero() {
	value, err := SourceTuple()
	_ = err
	Sink(value)
}

// Negative_clean: no tuple source reaches the sink.
func Negative_clean() {
	Sink("safe")
}
