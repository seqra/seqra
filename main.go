package main

import (
	"github.com/seqra/seqra/cmd"
	"github.com/seqra/seqra/internal/utils/log"
	"github.com/sirupsen/logrus"
)

func main() {
	// Ensure log file is properly closed when main exits
	defer func() {
		if err := log.CloseLogFile(); err != nil {
			logrus.Fatalf("Unexpected error: %s", err)
		}
	}()

	cmd.Execute()
}
