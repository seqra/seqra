package autobuilder

import (
	"os"
	"path/filepath"
	"testing"
)

func writeLog(t *testing.T, content string) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "autobuild.log")
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
	return path
}

func TestCompilerDiagnostics(t *testing.T) {
	tests := []struct {
		name string
		log  string
		want string
	}{
		{
			name: "javac error among the build trace",
			log: `20:22:13.627 |I| ProjectResolver - Gradle build start for: /tmp/batch
20:22:13.628 |D| ProjectResolver - /tmp/batch/src/main/java/Model.java:4: error: package org.nosuchlib does not exist
20:22:13.629 |D| ProjectResolver - import org.nosuchlib.Missing;
20:22:13.629 |D| ProjectResolver - 1 error
20:22:13.630 |D| ProjectResolver - FAILURE: Build failed with an exception.
`,
			want: "  /tmp/batch/src/main/java/Model.java:4: error: package org.nosuchlib does not exist\n  1 error",
		},
		{
			name: "kotlinc diagnostics",
			log:  "12:00:00.000 |D| ProjectResolver - e: file:///tmp/batch/Model.kt:7:1 unresolved reference\n",
			want: "  e: file:///tmp/batch/Model.kt:7:1 unresolved reference",
		},
		{
			name: "plural error count",
			log:  "12:00:00.000 |D| ProjectResolver - 3 errors\n",
			want: "  3 errors",
		},
		{
			name: "raw build output with no log prefix",
			log:  "Model.java:8: error: cannot find symbol\n",
			want: "  Model.java:8: error: cannot find symbol",
		},
		{
			name: "successful build has no diagnostics",
			log: `20:22:13.627 |I| ProjectResolver - Gradle build start for: /tmp/batch
20:22:13.630 |I| ProjectResolver - BUILD SUCCESSFUL in 4s
`,
			want: "",
		},
		{
			name: "an error mentioned in prose is not a diagnostic",
			log:  "12:00:00.000 |I| ProjectResolver - Gradle build failed for: /tmp/batch\n",
			want: "",
		},
		{
			name: "windows line endings are trimmed",
			log:  "12:00:00.000 |D| ProjectResolver - Model.java:8: error: cannot find symbol\r\n",
			want: "  Model.java:8: error: cannot find symbol",
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if got := CompilerDiagnostics(writeLog(t, test.log)); got != test.want {
				t.Errorf("CompilerDiagnostics() =\n%q\nwant\n%q", got, test.want)
			}
		})
	}
}

func TestCompilerDiagnosticsMissingLog(t *testing.T) {
	if got := CompilerDiagnostics(filepath.Join(t.TempDir(), "absent.log")); got != "" {
		t.Errorf("CompilerDiagnostics() = %q, want \"\" for an unreadable log", got)
	}
}
