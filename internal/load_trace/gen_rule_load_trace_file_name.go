package load_trace

import (
	"os"
	"path/filepath"
	"time"
)

// PathBuilder provides a fluent interface for constructing trace file paths
type PathBuilder struct {
	baseDir     string
	subDir      string
	filePrefix  string
	fileSuffix  string
	timeFormat  string
	permissions os.FileMode
}

// NewPathBuilder creates a new PathBuilder with default settings
func NewPathBuilder() *PathBuilder {
	return &PathBuilder{
		baseDir:     os.TempDir(),
		subDir:      filepath.Join("seqra", "rule_load_trace"),
		filePrefix:  "",
		fileSuffix:  ".json",
		timeFormat:  "2006-01-02_15-04-05",
		permissions: 0755,
	}
}

// BaseDir sets the base directory for trace files
func (pb *PathBuilder) BaseDir(dir string) *PathBuilder {
	pb.baseDir = dir
	return pb
}

// SubDir sets the subdirectory within the base directory
func (pb *PathBuilder) SubDir(dir string) *PathBuilder {
	pb.subDir = dir
	return pb
}

// FilePrefix sets the prefix for the generated filename
func (pb *PathBuilder) FilePrefix(prefix string) *PathBuilder {
	pb.filePrefix = prefix
	return pb
}

// FileSuffix sets the suffix (extension) for the generated filename
func (pb *PathBuilder) FileSuffix(suffix string) *PathBuilder {
	pb.fileSuffix = suffix
	return pb
}

// TimeFormat sets the time format used in the filename
func (pb *PathBuilder) TimeFormat(format string) *PathBuilder {
	pb.timeFormat = format
	return pb
}

// Permissions sets the directory permissions
func (pb *PathBuilder) Permissions(perm os.FileMode) *PathBuilder {
	pb.permissions = perm
	return pb
}

// Build generates the trace file path and creates necessary directories
func (pb *PathBuilder) Build() (string, error) {
	dir := filepath.Join(pb.baseDir, pb.subDir)
	filename := pb.filePrefix + time.Now().Format(pb.timeFormat) + pb.fileSuffix
	filePath := filepath.Join(dir, filename)

	if err := os.MkdirAll(dir, pb.permissions); err != nil {
		return "", err
	}

	return filePath, nil
}

// GenerateSemgrepRuleLoadTraceFilePath creates a trace file path using default settings (backward compatibility)
func GenerateSemgrepRuleLoadTraceFilePath() (string, error) {
	return NewPathBuilder().Build()
}
