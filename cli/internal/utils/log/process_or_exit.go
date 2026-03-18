package log

import (
	"path/filepath"

	"github.com/seqra/opentaint/internal/output"
)

func AbsPathOrExit(relativePath, identifier string) string {
	absPath, err := filepath.Abs(relativePath)
	if err != nil {
		output.LogInfof("Failed to convert %s \"%s\" to absolute path", identifier, relativePath)
		output.Fatal(err)
	}
	return absPath
}
