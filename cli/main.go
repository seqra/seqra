package main

import (
	"fmt"
	"os"

	"github.com/seqra/opentaint/v2/cmd"
	"github.com/seqra/opentaint/v2/internal/utils/log"
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
