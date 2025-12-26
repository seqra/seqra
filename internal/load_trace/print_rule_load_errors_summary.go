package load_trace

type RuleLoadErrorsResult struct {
	Summary RuleLoadErrorsAggregatedSummary
	Error   error
}

type RuleLoadErrorsAggregatedSummary struct {
	// File-level: count number of files that have each error type.
	FileErrorTypes map[errorCategory]int

	// Rule-level: count number of rules that have each error type.
	RuleErrorTypes map[errorCategory]int

	// Number of distinct files affected by any type of error
	TotalAffectedFiles int
	// Number of distinct rules affected by any type of error
	TotalAffectedRules int
}

func CollectRulesetLoadErrorsSummary(ruleLoadTraceSummary RuleLoadTraceSummary) RuleLoadErrorsResult {
	summary := AggregateRuleLoadErrorsSummary(ruleLoadTraceSummary)
	return RuleLoadErrorsResult{Summary: summary, Error: nil}
}

func AggregateRuleLoadErrorsSummary(loadTraceSummary RuleLoadTraceSummary) RuleLoadErrorsAggregatedSummary {
	summary := newSummary()

	// File level
	for _, file := range loadTraceSummary.Files {
		hasAnyFileLevelError := false

		fileSeen := map[errorCategory]bool{}
		for category := range file.errorTypes {
			fileSeen[category] = true
			hasAnyFileLevelError = true
		}
		for category := range fileSeen {
			summary.FileErrorTypes[category]++
		}

		// Rule level
		for _, rule := range file.Rules {
			ruleSeen := map[errorCategory]bool{}
			for category := range rule.errorTypes {
				ruleSeen[category] = true
			}

			// Step-level errors (considered as rule errors)
			for _, step := range rule.Steps {
				for category := range step.errorTypes {
					ruleSeen[category] = true
				}
			}

			if len(ruleSeen) > 0 {
				// This rule is affected by at least one error type
				summary.TotalAffectedRules++
			}

			for category := range ruleSeen {
				summary.RuleErrorTypes[category]++
			}
		}

		if hasAnyFileLevelError {
			summary.TotalAffectedFiles++
		}
	}

	return summary
}

func newSummary() RuleLoadErrorsAggregatedSummary {
	return RuleLoadErrorsAggregatedSummary{
		FileErrorTypes: map[errorCategory]int{
			SyntaxError: 0, Unsupported: 0,
		},
		RuleErrorTypes: map[errorCategory]int{
			SyntaxError: 0, Unsupported: 0,
		},
		TotalAffectedFiles: 0,
		TotalAffectedRules: 0,
	}
}

type RuleLoadTraceSummary struct {
	Files []fileSummary
}

type fileSummary struct {
	Path       string
	Errors     []errorEntry
	errorTypes map[errorCategory]struct{}
	Rules      []ruleSummary
}

type ruleSummary struct {
	RuleID     string
	Errors     []errorEntry
	errorTypes map[errorCategory]struct{}
	Steps      []stepSummary
}

type stepSummary struct {
	Step       Step
	Errors     []errorEntry
	errorTypes map[errorCategory]struct{}
}

type errorEntry struct {
	Message string
	Type    errorCategory
}

func CollectRuleLoadTraceSummary(trace *SemgrepLoadTrace) RuleLoadTraceSummary {
	out := RuleLoadTraceSummary{}

	if trace.FileTraces == nil {
		return out
	}

	for _, fileTrace := range trace.FileTraces {
		fileSummary := fileSummary{
			errorTypes: make(map[errorCategory]struct{}),
			Path:       "",
		}

		fileSummary.Path = fileTrace.Path
		fileSummary.Errors, fileSummary.errorTypes = collectFromEntries(fileTrace.Entries)

		if fileTrace.RuleTraces != nil {
			for _, rt := range fileTrace.RuleTraces {
				ruleSummary := ruleSummary{
					errorTypes: make(map[errorCategory]struct{}),
				}
				ruleSummary.RuleID = rt.RuleId
				ruleSummary.Errors, ruleSummary.errorTypes = collectFromEntries(rt.Entries)

				if rt.Steps != nil {
					for _, st := range rt.Steps {
						stepSummary := stepSummary{
							errorTypes: make(map[errorCategory]struct{}),
						}

						stepSummary.Step = st.Step
						stepSummary.Errors, stepSummary.errorTypes = collectFromEntries(st.Entries)
						ruleSummary.Steps = append(ruleSummary.Steps, stepSummary)
					}
				}

				fileSummary.Rules = append(fileSummary.Rules, ruleSummary)
			}
		}

		out.Files = append(out.Files, fileSummary)
	}

	return out
}

func collectFromEntries(entries []TraceEntry) ([]errorEntry, map[errorCategory]struct{}) {
	var errs []errorEntry
	categories := make(map[errorCategory]struct{})

	for _, entry := range entries {
		if entry.IsError() {
			errCategory := classifyError(&entry)
			if errCategory == nil {
				continue
			}

			category := *errCategory
			msg := entry.Message

			errs = append(errs, errorEntry{
				Message: msg,
				Type:    category,
			})

			categories[category] = struct{}{}
		}
	}

	return errs, categories
}

type errorCategory int

const (
	SyntaxError errorCategory = iota
	// Unsupported Seqra not implemented, seqra internal errors, Seqra is expected to be unsupported
	Unsupported
)

var userErrorSteps = map[Step]struct{}{
	StepLoadRuleset:           {},
	StepBuildConvertToRawRule: {},
	StepBuildParseSemgrepRule: {},
}

func classifyError(errEntry *TraceEntry) *errorCategory {
	if errEntry.Type != "Error" {
		return nil
	}

	if errEntry.Reason == ReasonNotImplemented {
		c := Unsupported
		return &c
	}

	if _, ok := userErrorSteps[errEntry.Step]; ok {
		c := SyntaxError
		return &c
	}

	c := Unsupported
	return &c
}
