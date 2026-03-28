package test

// ── Goroutine and channel tests ──────────────────────────────────────

// ── Channel send/receive ─────────────────────────────────────────────

func channel001T() {
	data := source()
	ch := make(chan string, 1)
	ch <- data
	result := <-ch
	sink(result)
}

func channel002F() {
	_ = source()
	ch := make(chan string, 1)
	ch <- "safe"
	result := <-ch
	sink(result)
}

// ── Goroutine with channel ───────────────────────────────────────────

func goroutineChan001T() {
	data := source()
	ch := make(chan string, 1)
	go func() {
		ch <- data
	}()
	result := <-ch
	sink(result)
}

func goroutineChan002F() {
	_ = source()
	ch := make(chan string, 1)
	go func() {
		ch <- "safe"
	}()
	result := <-ch
	sink(result)
}

// ── Goroutine with shared variable (through closure) ─────────────────

func goroutineShared001T() {
	data := source()
	done := make(chan bool, 1)
	var result string
	go func() {
		result = data
		done <- true
	}()
	<-done
	sink(result)
}

// ── Buffered channel ─────────────────────────────────────────────────

func bufferedChan001T() {
	data := source()
	ch := make(chan string, 10)
	ch <- data
	ch <- "extra"
	result := <-ch
	sink(result)
}

func bufferedChan002F() {
	_ = source()
	ch := make(chan string, 10)
	ch <- "safe"
	result := <-ch
	sink(result)
}

// ── Channel passed to function ───────────────────────────────────────

func sendToChan(ch chan string, val string) {
	ch <- val
}

func chanArg001T() {
	data := source()
	ch := make(chan string, 1)
	sendToChan(ch, data)
	result := <-ch
	sink(result)
}

func chanArg002F() {
	_ = source()
	ch := make(chan string, 1)
	sendToChan(ch, "safe")
	result := <-ch
	sink(result)
}
