package test

import "errors"

// ── Error handling pattern tests ─────────────────────────────────────

// ── Error return value ───────────────────────────────────────────────

func mayFail(input string) (string, error) {
	if input == "" {
		return "", errors.New("empty")
	}
	return input, nil
}

func mayFailClean(input string) (string, error) {
	return "safe", nil
}

func errorReturn001T() {
	data := source()
	result, err := mayFail(data)
	if err == nil {
		sink(result)
	}
}

func errorReturn002F() {
	data := source()
	result, err := mayFailClean(data)
	if err == nil {
		sink(result)
	}
}

func errorReturn003T() {
	data := source()
	result, _ := mayFail(data)
	sink(result)
}

// ── Error wrapping ───────────────────────────────────────────────────

func wrapResult(input string) (string, error) {
	r, err := mayFail(input)
	if err != nil {
		return "", err
	}
	return r, nil
}

func errorWrap001T() {
	data := source()
	result, _ := wrapResult(data)
	sink(result)
}

func errorWrap002F() {
	data := source()
	_, _ = wrapResult(data) // discard result
	sink("safe")
}

// ── Early return on error ────────────────────────────────────────────

func earlyReturn001T() {
	data := source()
	result, err := mayFail(data)
	if err != nil {
		return
	}
	sink(result)
}

func earlyReturn002F() {
	data := source()
	_, err := mayFail(data)
	if err != nil {
		return
	}
	sink("safe")
}
