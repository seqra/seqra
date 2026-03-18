package main

import (
	"fmt"
	"os"

	"github.com/seqra/opentaint/cmd"
	"github.com/seqra/opentaint/internal/utils/log"
)

func main() {
	// Ensure log file is properly closed when main exits
	defer func() {
		if err := log.CloseLogFile(); err != nil {
			fmt.Fprintf(os.Stderr, "Failed to close log file: %s\n", err)
		}
	}()

	cmd.Execute()
}
