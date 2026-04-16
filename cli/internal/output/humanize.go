package output

import (
	"unicode"
	"unicode/utf8"
)

// Humanize returns the error's message with its first letter capitalized,
// suitable for displaying to end users. Go convention keeps error strings
// lowercase, but CLI output should read like a sentence.
func Humanize(err error) string {
	msg := err.Error()
	r, size := utf8.DecodeRuneInString(msg)
	if size == 0 || r == utf8.RuneError {
		return msg
	}
	upper := unicode.ToUpper(r)
	if upper == r {
		return msg
	}
	return string(upper) + msg[size:]
}
