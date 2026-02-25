package formatters

import (
	"fmt"
	"strings"

	"github.com/seqra/seqra/v2/internal/utils/color"
)

func FormatFilePathHyperLink(absProjectPath string, relFilePath string, fileName string, line int64) string {
	if !color.Enabled() {
		return fmt.Sprintf("%s:%d", fileName, line)
	}

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
