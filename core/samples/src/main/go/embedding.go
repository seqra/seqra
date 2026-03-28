package test

// ── Struct embedding tests ───────────────────────────────────────────

// ── Basic embedding ──────────────────────────────────────────────────

type EmbBase struct {
	baseValue string
}

func (b EmbBase) GetBaseValue() string { return b.baseValue }

type EmbDerived struct {
	EmbBase
	derivedValue string
}

func embeddedField001T() {
	data := source()
	d := EmbDerived{
		EmbBase:      EmbBase{baseValue: data},
		derivedValue: "safe",
	}
	sink(d.baseValue) // access promoted field
}

func embeddedField002F() {
	data := source()
	d := EmbDerived{
		EmbBase:      EmbBase{baseValue: data},
		derivedValue: "safe",
	}
	sink(d.derivedValue)
}

func embeddedMethod001T() {
	data := source()
	d := EmbDerived{
		EmbBase:      EmbBase{baseValue: data},
		derivedValue: "safe",
	}
	result := d.GetBaseValue() // promoted method
	sink(result)
}

func embeddedMethod002F() {
	_ = source()
	d := EmbDerived{
		EmbBase:      EmbBase{baseValue: "safe"},
		derivedValue: "safe",
	}
	result := d.GetBaseValue()
	sink(result)
}

// ── Multi-level embedding ────────────────────────────────────────────

type EmbLevel2 struct {
	EmbDerived
	level2Value string
}

func embeddedDeep001T() {
	data := source()
	obj := EmbLevel2{
		EmbDerived: EmbDerived{
			EmbBase:      EmbBase{baseValue: data},
			derivedValue: "safe",
		},
		level2Value: "safe",
	}
	sink(obj.baseValue) // promoted through two levels
}

func embeddedDeep002F() {
	data := source()
	obj := EmbLevel2{
		EmbDerived: EmbDerived{
			EmbBase:      EmbBase{baseValue: data},
			derivedValue: "safe",
		},
		level2Value: "safe",
	}
	sink(obj.level2Value)
}

// ── Interface embedding ──────────────────────────────────────────────

type EmbReader interface {
	Read() string
}

type EmbWriter interface {
	Write(data string)
}

type EmbReadWriter interface {
	EmbReader
	EmbWriter
}

type EmbRWImpl struct {
	data string
}

func (rw *EmbRWImpl) Read() string      { return rw.data }
func (rw *EmbRWImpl) Write(data string) { rw.data = data }

func embeddedInterface001T() {
	data := source()
	var rw EmbReadWriter = &EmbRWImpl{data: data}
	result := rw.Read()
	sink(result)
}

func embeddedInterface002F() {
	_ = source()
	var rw EmbReadWriter = &EmbRWImpl{data: "safe"}
	result := rw.Read()
	sink(result)
}
