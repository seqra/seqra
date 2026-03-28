package test

// ── Method receiver tests ────────────────────────────────────────────

// MRContainer value receiver
type MRContainer struct {
	value string
}

func (c MRContainer) GetValue() string { return c.value }

// MRPtrContainer pointer receiver
type MRPtrContainer struct {
	value string
}

func (c *MRPtrContainer) SetValue(v string) { c.value = v }
func (c *MRPtrContainer) GetValue() string  { return c.value }

// MRMultiField has multiple fields
type MRMultiField struct {
	first  string
	second string
}

func (m MRMultiField) GetFirst() string  { return m.first }
func (m MRMultiField) GetSecond() string { return m.second }

// ── Value receiver tests ─────────────────────────────────────────────

func methodRecvValue001T() {
	data := source()
	c := MRContainer{value: data}
	result := c.GetValue()
	sink(result)
}

func methodRecvValue002F() {
	_ = source()
	c := MRContainer{value: "safe"}
	result := c.GetValue()
	sink(result)
}

// ── Pointer receiver tests ───────────────────────────────────────────

func methodRecvPtr001T() {
	data := source()
	c := &MRPtrContainer{}
	c.SetValue(data)
	result := c.GetValue()
	sink(result)
}

func methodRecvPtr002F() {
	_ = source()
	c := &MRPtrContainer{}
	c.SetValue("safe")
	result := c.GetValue()
	sink(result)
}

func methodRecvPtr003T() {
	data := source()
	c := &MRPtrContainer{value: data}
	result := c.GetValue()
	sink(result)
}

// ── Multi-field receiver ─────────────────────────────────────────────

func methodRecvField001T() {
	data := source()
	m := MRMultiField{first: data, second: "safe"}
	result := m.GetFirst()
	sink(result)
}

func methodRecvField002F() {
	data := source()
	m := MRMultiField{first: data, second: "safe"}
	result := m.GetSecond()
	sink(result)
}

// ── Chained method calls ─────────────────────────────────────────────

type MRChain struct {
	data string
}

func (c MRChain) Transform() MRChain { return MRChain{data: c.data} }
func (c MRChain) GetData() string    { return c.data }

func methodChain001T() {
	data := source()
	c := MRChain{data: data}
	result := c.Transform().GetData()
	sink(result)
}

func methodChain002F() {
	_ = source()
	c := MRChain{data: "safe"}
	result := c.Transform().GetData()
	sink(result)
}
