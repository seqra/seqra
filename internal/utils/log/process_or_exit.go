package log

import (
	"path/filepath"

	"github.com/sirupsen/logrus"
)

func AbsPathOrExit(relativePath, identifier string) string {
	absPath, err := filepath.Abs(relativePath)
	if err != nil {
		logrus.Errorf("Failed to convert %s \"%s\" to absolute path", identifier, relativePath)
		logrus.Fatal(err)
	}
	return absPath
}
