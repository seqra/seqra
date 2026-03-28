package test

// ── Variable shadowing tests ─────────────────────────────────────────

func shadow001T() {
	data := source()
	{
		// inner block: data still visible
		sink(data)
	}
}

func shadow002F() {
	data := source()
	func() {
		data := "safe" // shadowed in inner func
		sink(data)
	}()
	consume(data)
}

func shadow003T() {
	data := "safe"
	func() {
		data := source() // shadowed with tainted in inner func
		sink(data)
	}()
	consume(data)
}

func shadow004F() {
	data := source()
	if true {
		data = "safe" // overwritten
	}
	sink(data)
}

// ── Shadowing in for loop ────────────────────────────────────────────

func shadowLoop001T() {
	data := source()
	for i := 0; i < 1; i++ {
		result := data // new variable
		sink(result)
	}
}

func shadowLoop002F() {
	data := source()
	for i := 0; i < 1; i++ {
		local := "safe"
		sink(local)
	}
	consume(data)
}

// ── Shadowing with function params ───────────────────────────────────

func shadowParam001T() {
	data := source()
	shadowHelper(data)
}

func shadowHelper(data string) {
	sink(data)
}

func shadowParam002F() {
	data := source()
	_ = data
	shadowHelper("safe")
}
