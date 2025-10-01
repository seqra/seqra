package log

import (
	"encoding/json"
	"fmt"
	"io"
	"strings"
)

func DisplayInteractiveProgress(rd io.Reader) {
	decoder := json.NewDecoder(rd)
	layers := make(map[string]string)
	var layerOrder []string
	var outputLines int

	for {
		var progress struct {
			Status         string `json:"status"`
			Progress       string `json:"progress"`
			ProgressDetail struct {
				Current int64 `json:"current"`
				Total   int64 `json:"total"`
			} `json:"progressDetail"`
			ID string `json:"id"`
		}

		if err := decoder.Decode(&progress); err != nil {
			if err == io.EOF {
				break
			}
			continue
		}

		if progress.ID != "" {
			if _, exists := layers[progress.ID]; !exists {
				layerOrder = append(layerOrder, progress.ID)
			}

			if progress.Progress != "" {
				layers[progress.ID] = fmt.Sprintf("%s: %s %s", progress.ID, progress.Status, progress.Progress)
			} else {
				layers[progress.ID] = fmt.Sprintf("%s: %s", progress.ID, progress.Status)
			}
		} else {
			layers["message_"+progress.Status] = progress.Status
			layerOrder = append(layerOrder, "message_"+progress.Status)
		}

		if outputLines > 0 {
			fmt.Printf("\033[%dA", outputLines)
			for range outputLines {
				fmt.Printf("\033[K\n")
			}
			fmt.Printf("\033[%dA", outputLines)
		}

		outputLines = 0
		for _, id := range layerOrder {
			if status, exists := layers[id]; exists {
				fmt.Println(status)
				outputLines += strings.Count(status, "\n") + 1
			}
		}
	}

	if outputLines > 0 {
		fmt.Printf("\033[%dA", outputLines)
		for range outputLines {
			fmt.Printf("\033[K\n")
		}
		fmt.Printf("\033[%dA", outputLines)
	}
}
