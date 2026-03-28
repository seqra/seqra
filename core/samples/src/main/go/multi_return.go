package test

// ── Multiple return value tests ──────────────────────────────────────

func twoReturns(a string, b string) (string, string) {
	return a, b
}

func threeReturns(a string, b string, c string) (string, string, string) {
	return a, b, c
}

// Named return values
func namedReturns(input string) (result string, err string) {
	result = input
	err = ""
	return
}

func namedReturnsClean(input string) (result string, err string) {
	result = "safe"
	err = ""
	return
}

// ── Basic multi-return ───────────────────────────────────────────────

func multiReturn001T() {
	data := source()
	first, _ := twoReturns(data, "clean")
	sink(first)
}

func multiReturn002F() {
	data := source()
	_, second := twoReturns(data, "clean")
	sink(second)
}

func multiReturn003T() {
	data := source()
	_, second := twoReturns("clean", data)
	sink(second)
}

func multiReturn004F() {
	data := source()
	first, _ := twoReturns("clean", data)
	sink(first)
}

// ── Three return values ──────────────────────────────────────────────

func threeReturn001T() {
	data := source()
	first, _, _ := threeReturns(data, "b", "c")
	sink(first)
}

func threeReturn002F() {
	data := source()
	_, second, _ := threeReturns(data, "b", "c")
	sink(second)
}

func threeReturn003T() {
	data := source()
	_, _, third := threeReturns("a", "b", data)
	sink(third)
}

// ── Named return values ──────────────────────────────────────────────

func namedReturn001T() {
	data := source()
	result, _ := namedReturns(data)
	sink(result)
}

func namedReturn002F() {
	data := source()
	result, _ := namedReturnsClean(data)
	sink(result)
}

// ── Discard with blank identifier ────────────────────────────────────

func blankIdentifier001T() {
	data := source()
	result, _ := twoReturns(data, "x")
	sink(result)
}

func blankIdentifier002F() {
	data := source()
	_, result := twoReturns(data, "x")
	sink(result)
}
