package test

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
