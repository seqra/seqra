package test

// ── Expression tests (binary ops, string concat, unary ops) ──────────

// ── String concatenation ─────────────────────────────────────────────

func stringConcat001T() {
	data := source()
	result := "prefix_" + data
	sink(result)
}

func stringConcat002T() {
	data := source()
	result := data + "_suffix"
	sink(result)
}

func stringConcat003T() {
	data := source()
	result := "prefix_" + data + "_suffix"
	sink(result)
}

func stringConcat004F() {
	_ = source()
	result := "prefix_" + "safe" + "_suffix"
	sink(result)
}

// ── String concatenation with += ─────────────────────────────────────

func stringConcatAssign001T() {
	data := source()
	result := "prefix_"
	result += data
	sink(result)
}

func stringConcatAssign002F() {
	_ = source()
	result := "prefix_"
	result += "safe"
	sink(result)
}

// ── Multiple concatenation chain ─────────────────────────────────────

func stringConcatChain001T() {
	data := source()
	a := "a" + data
	b := a + "b"
	c := b + "c"
	sink(c)
}

func stringConcatChain002F() {
	_ = source()
	a := "a" + "safe"
	b := a + "b"
	c := b + "c"
	sink(c)
}

// ── Integer arithmetic (kills taint) ─────────────────────────────────

func intArith001F() {
	data := sourceInt()
	result := data + 1
	sinkInt(result)
}

func intArith002F() {
	data := sourceInt()
	result := data * 2
	sinkInt(result)
}

// ── Boolean negation (kills taint) ───────────────────────────────────

func boolNeg001F() {
	data := sourceBool()
	result := !data
	sinkBool(result)
}

// ── Comparison (kills taint) ─────────────────────────────────────────

func comparison001F() {
	data := source()
	result := data == "test"
	sinkBool(result)
}
