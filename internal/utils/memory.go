package utils

import (
	"errors"
	"regexp"
	"strconv"
	"strings"
)

func ParseMemoryValue(memory string) (string, error) {
	if memory == "" {
		return "", errors.New("memory value cannot be empty")
	}

	// Check if it's just a number (bytes)
	if value, err := strconv.ParseInt(memory, 10, 64); err == nil {
		return "-Xmx" + strconv.FormatInt(value, 10), nil
	}

	pattern := regexp.MustCompile(`^(\d+)([kmgKMG])$`)
	matches := pattern.FindStringSubmatch(memory)

	if len(matches) != 3 {
		return "", errors.New("invalid memory format, expected format like 1024m, 81920k, 8G, or 83886080")
	}

	value, err := strconv.ParseInt(matches[1], 10, 64)
	if err != nil {
		return "", err
	}

	unit := strings.ToLower(matches[2])
	return "-Xmx" + strconv.FormatInt(value, 10) + unit, nil
}
