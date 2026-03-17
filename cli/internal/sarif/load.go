package sarif

import (
	"fmt"
	"os"
)

func LoadReport(path string) (*Report, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("failed to read sarif report: %w", err)
	}

	report, err := UnmarshalReport(data)
	if err != nil {
		return nil, fmt.Errorf("failed to parse sarif report: %w", err)
	}

	return &report, nil
}
