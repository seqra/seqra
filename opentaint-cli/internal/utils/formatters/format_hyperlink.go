package formatters

import (
	"fmt"
	"strings"
)

func FormatFilePathHyperLink(absProjectPath string, relFilePath string, fileName string, line int64) string {
	absProjectPath = strings.ReplaceAll(absProjectPath, "\\", "/")

	if !strings.HasSuffix(absProjectPath, "/") {
		absProjectPath += "/"
	}

	uri := fmt.Sprintf("file://%s%s", absProjectPath, relFilePath)

	esc := "\033"
	link := esc + "]8;;" + uri + esc + "\\" +
		fileName +
		esc + "]8;;" + esc + "\\"
	return fmt.Sprintf("%s:%d", link, line)
}
