package test

func source() string {
	return "tainted"
}

func sink(data string) {
	consume(data)
}

func consume(str string) {
	_ = str
}
