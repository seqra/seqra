package test

// ── Channel direction and usage pattern tests ────────────────────────

// ── Directional channels ─────────────────────────────────────────────

func chanDirection001T() {
	ch := make(chan string, 1)
	data := source()
	ch <- data
	result := <-ch
	sink(result)
}

func chanDirection002F() {
	ch := make(chan string, 1)
	_ = source()
	ch <- "safe"
	result := <-ch
	sink(result)
}

// ── Multiple sends on a channel ──────────────────────────────────────

func chanMultiSend001T() {
	ch := make(chan string, 3)
	ch <- "clean1"
	ch <- source()
	ch <- "clean2"
	_ = <-ch
	result := <-ch
	sink(result)
}

func chanMultiSend002F() {
	_ = source()
	ch := make(chan string, 3)
	ch <- "clean1"
	ch <- "clean2"
	ch <- "clean3"
	_ = <-ch
	result := <-ch
	sink(result)
}

// ── Function returning value from channel ────────────────────────────

func recvFromChan(ch chan string) string {
	return <-ch
}

func chanFunc001T() {
	ch := make(chan string, 1)
	data := source()
	ch <- data
	result := recvFromChan(ch)
	sink(result)
}

func chanFunc002F() {
	ch := make(chan string, 1)
	_ = source()
	ch <- "safe"
	result := recvFromChan(ch)
	sink(result)
}

// ── Channel passed to function that sends ────────────────────────────

func chanSendHelper(ch chan string, val string) {
	ch <- val
}

func chanPassThrough001T() {
	ch := make(chan string, 1)
	data := source()
	chanSendHelper(ch, data)
	result := <-ch
	sink(result)
}

func chanPassThrough002F() {
	ch := make(chan string, 1)
	_ = source()
	chanSendHelper(ch, "safe")
	result := <-ch
	sink(result)
}

// ── Channel receive in loop ──────────────────────────────────────────

func chanLoop001T() {
	ch := make(chan string, 3)
	ch <- source()
	ch <- "a"
	ch <- "b"
	for i := 0; i < 3; i++ {
		val := <-ch
		if i == 0 {
			sink(val)
		}
	}
}

func chanLoop002F() {
	_ = source()
	ch := make(chan string, 3)
	ch <- "safe1"
	ch <- "safe2"
	ch <- "safe3"
	for i := 0; i < 3; i++ {
		val := <-ch
		if i == 0 {
			sink(val)
		}
	}
}
