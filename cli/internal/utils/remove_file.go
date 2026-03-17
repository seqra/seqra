package utils

import (
	"os"

	"github.com/seqra/opentaint/v2/internal/output"
)

func RemoveIfExists(path string) error {
	err := os.Remove(path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return err
	}
	return nil
}

func RemoveIfExistsOrExit(path string) {
	if err := RemoveIfExists(path); err != nil {
		output.Fatalf("Failed to remove '%s': %s", path, err)
	}
}
