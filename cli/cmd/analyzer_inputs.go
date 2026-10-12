package cmd

import (
	"github.com/seqra/opentaint/internal/approximation"
	"github.com/seqra/opentaint/internal/utils/log"
)

// addDataflowApproximations resolves the paths to compiled model directories.
// It rebuilds a model project when its inputs change.
func addDataflowApproximations(b *AnalyzerBuilder, paths []string, analyzerJarPath string) {
	if len(paths) == 0 {
		return
	}

	absPaths := make([]string, 0, len(paths))
	for _, approxPath := range paths {
		absPaths = append(absPaths, log.AbsPathOrExit(approxPath, "dataflow-approximations"))
	}

	classDirs, err := approximation.ResolveAll(absPaths, newApproximationBuilder(analyzerJarPath))
	if err != nil {
		out.Fatalf("Failed to apply dataflow approximations: %s", err)
	}
	for _, classDir := range classDirs {
		b.AddDataflowApproximations(classDir)
	}
}

func addPassthroughApproximations(b *AnalyzerBuilder, paths []string) {
	for _, passthrough := range paths {
		b.AddPassthroughApproximations(log.AbsPathOrExit(passthrough, "passthrough-approximations"))
	}
}
