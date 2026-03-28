package test

// ── Variadic function tests ──────────────────────────────────────────

func varJoin(parts ...string) string {
	result := ""
	for _, p := range parts {
		result = result + p
	}
	return result
}

func varFirst(parts ...string) string {
	if len(parts) > 0 {
		return parts[0]
	}
	return ""
}

func varLast(parts ...string) string {
	if len(parts) > 0 {
		return parts[len(parts)-1]
	}
	return ""
}

// ── Tests ────────────────────────────────────────────────────────────

func variadic001T() {
	data := source()
	result := varJoin(data, "b", "c")
	sink(result)
}

func variadic002F() {
	_ = source()
	result := varJoin("a", "b", "c")
	sink(result)
}

func variadic003T() {
	data := source()
	result := varFirst(data, "b")
	sink(result)
}

func variadic004F() {
	data := source()
	result := varFirst("safe", data)
	// varFirst returns first arg which is "safe"
	sink(result)
}

func variadic005T() {
	data := source()
	result := varLast("a", data)
	sink(result)
}

func variadic006F() {
	data := source()
	result := varLast(data, "safe")
	// varLast returns last arg which is "safe"
	sink(result)
}

// ── Spread slice into variadic ───────────────────────────────────────

func variadicSpread001T() {
	data := source()
	args := []string{data, "b"}
	result := varFirst(args...)
	sink(result)
}

func variadicSpread002T() {
	data := source()
	args := []string{"a", data}
	result := varJoin(args...)
	sink(result)
}
