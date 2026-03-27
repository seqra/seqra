package sample

func source() string {
	return "tainted"
}

func sink(data string) {
	consume(data)
}

func consume(str string) {
	_ = str
}

func sample() {
	var data = source()
	var other = data
	sink(other)
}

func sampleNonReachable() {
	var data = source()
	var other = "safe"
	sink(other)
	consume(data)
}
