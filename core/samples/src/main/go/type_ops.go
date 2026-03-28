package test

// ── Type operation tests (casts, assertions, conversions, interface wrapping) ──

// ── Type conversion (preserves taint) ────────────────────────────────

func typeCastInt001T() {
	data := sourceInt()
	result := float64(data)
	sinkFloat(result)
}

func typeCastInt002F() {
	_ = sourceInt()
	result := float64(42)
	sinkFloat(result)
}

func typeCastStringToBytes001T() {
	data := source()
	bytes := []byte(data)
	result := string(bytes)
	sink(result)
}

func typeCastStringToBytes002F() {
	_ = source()
	bytes := []byte("safe")
	result := string(bytes)
	sink(result)
}

// ── Interface wrapping (MakeInterface preserves taint) ───────────────

func interfaceWrap001T() {
	data := source()
	var iface interface{} = data
	result := iface.(string)
	sink(result)
}

func interfaceWrap002F() {
	_ = source()
	var iface interface{} = "safe"
	result := iface.(string)
	sink(result)
}

// ── Type assertion ───────────────────────────────────────────────────

func typeAssert001T() {
	data := sourceAny()
	result := data.(string)
	sink(result)
}

func typeAssert002F() {
	_ = sourceAny()
	var clean interface{} = "safe"
	result := clean.(string)
	sink(result)
}

// ── Type assertion with comma-ok ─────────────────────────────────────

func typeAssertOk001T() {
	data := sourceAny()
	result, ok := data.(string)
	if ok {
		sink(result)
	}
}

func typeAssertOk002F() {
	_ = sourceAny()
	var clean interface{} = "safe"
	result, ok := clean.(string)
	if ok {
		sink(result)
	}
}

// ── Rune/byte conversion ─────────────────────────────────────────────

func runeConv001T() {
	data := sourceInt()
	r := rune(data)
	result := string(r)
	sink(result)
}

func runeConv002F() {
	_ = sourceInt()
	r := rune(65)
	result := string(r)
	sink(result)
}
