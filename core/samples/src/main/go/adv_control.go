package test

// ── Advanced control flow tests ──────────────────────────────────────

// ── Switch/case ──────────────────────────────────────────────────────

func switchCase001T() {
	data := source()
	var result string
	switch data {
	case "a":
		result = data
	case "b":
		result = data
	default:
		result = data
	}
	sink(result)
}

func switchCase002F() {
	data := source()
	var result string
	switch data {
	case "a":
		result = "safe"
	case "b":
		result = "safe"
	default:
		result = "safe"
	}
	sink(result)
	consume(data)
}

func switchFallthrough001T() {
	data := source()
	var result string
	switch 1 {
	case 1:
		result = data
		fallthrough
	case 2:
		// result keeps value from case 1
	}
	sink(result)
}

// ── For-range ────────────────────────────────────────────────────────

func forRange001T() {
	data := source()
	s := []string{data, "a", "b"}
	var result string
	for _, v := range s {
		result = v
	}
	sink(result)
}

func forRange002F() {
	data := source()
	s := []string{"a", "b", "c"}
	var result string
	for _, v := range s {
		result = v
	}
	sink(result)
	consume(data)
}

func forRangeMap001T() {
	data := source()
	m := map[string]string{"k": data}
	var result string
	for _, v := range m {
		result = v
	}
	sink(result)
}

func forRangeMap002F() {
	data := source()
	m := map[string]string{"k": "safe"}
	var result string
	for _, v := range m {
		result = v
	}
	sink(result)
	consume(data)
}

// ── Break and continue ───────────────────────────────────────────────

func breakInLoop001T() {
	data := source()
	var result string
	for i := 0; i < 10; i++ {
		if i == 5 {
			result = data
			break
		}
	}
	sink(result)
}

func breakInLoop002F() {
	data := source()
	var result string
	for i := 0; i < 10; i++ {
		result = "safe"
		if i == 5 {
			break
		}
	}
	sink(result)
	consume(data)
}

func continueInLoop001T() {
	data := source()
	var result string
	for i := 0; i < 3; i++ {
		if i == 0 {
			continue
		}
		result = data
	}
	sink(result)
}

// ── Labeled break ────────────────────────────────────────────────────

func labeledBreak001T() {
	data := source()
	var result string
outer:
	for i := 0; i < 3; i++ {
		for j := 0; j < 3; j++ {
			if j == 1 {
				result = data
				break outer
			}
		}
	}
	sink(result)
}

// ── Nested loops ─────────────────────────────────────────────────────

func nestedLoop001T() {
	data := source()
	var result string
	for i := 0; i < 3; i++ {
		for j := 0; j < 3; j++ {
			result = data
		}
	}
	sink(result)
}

func nestedLoop002F() {
	data := source()
	var result string
	for i := 0; i < 3; i++ {
		for j := 0; j < 3; j++ {
			result = "safe"
		}
	}
	sink(result)
	consume(data)
}

// ── Select statement ─────────────────────────────────────────────────

func selectStmt001T() {
	data := source()
	ch := make(chan string, 1)
	ch <- data
	var result string
	select {
	case result = <-ch:
	}
	sink(result)
}
